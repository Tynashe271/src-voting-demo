package main

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/gin-contrib/cors"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/redis/go-redis/v9"
	"golang.org/x/crypto/argon2"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

type App struct {
	db             *gorm.DB
	redis          *redis.Client
	domain, secret string
	loc            *time.Location
}
type loginRequest struct {
	RegistrationNumber string `json:"registration_number" binding:"required,max=64"`
	Password           string `json:"password" binding:"required,max=128"`
}
type selection struct {
	PositionID  string `json:"position_id" binding:"required,uuid"`
	CandidateID string `json:"candidate_id" binding:"required,uuid"`
}
type ballotRequest struct {
	Selections []selection `json:"selections" binding:"required,min=1,max=30"`
}
type principal struct{ StudentID uuid.UUID }

var redisRateScript = redis.NewScript(`
local n = redis.call('INCR', KEYS[1])
if n == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
return n
`)

func main() {
	dsn := required("DATABASE_URL")
	db, err := gorm.Open(postgres.Open(dsn), &gorm.Config{TranslateError: true})
	if err != nil {
		panic(err)
	}
	loc, err := time.LoadLocation(env("UNIVERSITY_TIMEZONE", "Africa/Harare"))
	if err != nil {
		panic(err)
	}
	a := &App{db: db, domain: strings.ToLower(env("STUDENT_EMAIL_DOMAIN", "students.university.ac.zw")), secret: required("SESSION_SECRET"), loc: loc}
	if err := db.Exec("SELECT set_config('app.university_timezone', ?, false)", loc.String()).Error; err != nil {
		panic("University timezone configuration failed: " + err.Error())
	}
	opt, e := redis.ParseURL(required("REDIS_URL"))
	if e != nil {
		panic(e)
	}
	a.redis = redis.NewClient(opt)
	go a.electionEngine()
	r := gin.New()
	if err := r.SetTrustedProxies([]string{"127.0.0.1", "::1", "172.16.0.0/12", "10.0.0.0/8"}); err != nil {
		panic(err)
	}
	r.Use(gin.Recovery(), requestID(), securityHeaders(), bodyLimit(1<<20), a.rateLimit())
	r.Use(cors.New(cors.Config{AllowOrigins: []string{env("STUDENT_ORIGIN", "http://localhost:3000"), env("ADMIN_ORIGIN", "http://localhost:3001")}, AllowMethods: []string{"GET", "POST", "PATCH"}, AllowHeaders: []string{"Content-Type", "X-CSRF-Token"}, AllowCredentials: true, MaxAge: 12 * time.Hour}))
	v := r.Group("/api/v1")
	v.POST("/auth/login", a.login)
	v.POST("/auth/refresh", a.auth, a.csrf, a.refresh)
	v.POST("/auth/logout", a.auth, a.csrf, a.logout)
	v.GET("/auth/me", a.auth, a.me)
	v.GET("/students/me", a.auth, a.me)
	v.GET("/students/me/eligibility", a.auth, a.myEligibility)
	v.GET("/students/me/voting-status", a.auth, a.myVotingStatus)
	v.GET("/dashboard", a.auth, a.dashboard)
	v.GET("/elections/current", a.currentElection)
	v.GET("/elections/:id", a.election)
	v.GET("/elections/:id/status", a.status)
	v.GET("/elections/:id/positions", a.positions)
	v.GET("/elections/:id/candidates", a.candidates)
	v.GET("/positions/:id/candidates", a.positionCandidates)
	v.GET("/candidates/:id", a.candidate)
	v.GET("/elections/:id/eligibility", a.auth, a.eligibility)
	v.GET("/elections/:id/ballot", a.auth, a.ballot)
	v.POST("/elections/:id/ballots", a.auth, a.csrf, a.submitBallot)
	v.GET("/elections/:id/receipt", a.auth, a.receipt)
	v.GET("/elections/:id/turnout", a.turnout)
	v.GET("/elections/:id/results", a.results)
	v.GET("/notifications", a.auth, a.notifications)
	v.PATCH("/notifications/:id/read", a.auth, a.csrf, a.readNotification)
	admin := v.Group("/admin", a.internalAdmin)
	admin.GET("/elections/:id/turnout", a.turnout)
	admin.GET("/elections/:id/results", a.adminResults)
	admin.POST("/elections/:id/results/publish", a.adminPublishResults)
	admin.GET("/elections/:id/timing", a.adminTiming)
	admin.GET("/audit", a.audit)
	admin.GET("/elections", a.adminListElections)
	admin.GET("/elections/:id/positions", a.positions)
	admin.GET("/elections/:id/candidates", a.adminCandidates)
	admin.POST("/students", a.adminImportStudents)
	admin.POST("/elections", a.adminCreateElection)
	admin.PATCH("/elections/:id", a.adminUpdateElection)
	admin.POST("/elections/:id/positions", a.adminCreatePosition)
	admin.PATCH("/positions/:id", a.adminUpdatePosition)
	admin.DELETE("/positions/:id", a.adminDeletePosition)
	admin.POST("/positions/:id/candidates", a.adminCreateCandidate)
	admin.PATCH("/candidates/:id", a.adminUpdateCandidate)
	admin.PATCH("/candidates/:id/approve", a.adminSetCandidateApproval("APPROVED"))
	admin.PATCH("/candidates/:id/reject", a.adminSetCandidateApproval("REJECTED"))
	// Unlike approve/reject, a disqualification is allowed to go through even after the election
	// has opened (migration 006's trigger carve-out permits only this specific transition while
	// OPEN); votes already cast for the candidate are never altered or removed, but sealResults
	// excludes them from the sealed tally.
	admin.PATCH("/candidates/:id/disqualify", a.adminSetCandidateApproval("DISQUALIFIED"))
	admin.POST("/elections/:id/eligibility", a.adminSetEligibility)
	r.GET("/healthz", func(c *gin.Context) { c.JSON(200, gin.H{"status": "ok"}) })
	r.GET("/readyz", func(c *gin.Context) {
		ctx, cancel := context.WithTimeout(c.Request.Context(), 2*time.Second)
		defer cancel()
		if err := a.db.Exec("SELECT 1").Error; err != nil {
			fail(c, 503, "Service dependencies are unavailable.")
			return
		}
		if err := a.redis.Ping(ctx).Err(); err != nil {
			fail(c, 503, "Service dependencies are unavailable.")
			return
		}
		c.JSON(200, gin.H{"status": "ready"})
	})
	if err := r.Run(env("HTTP_ADDR", ":8080")); err != nil {
		panic(err)
	}
}
func env(k, d string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return d
}
func required(k string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	panic(k + " is required")
}
func requestID() gin.HandlerFunc {
	return func(c *gin.Context) { c.Header("X-Request-ID", uuid.NewString()); c.Next() }
}
func securityHeaders() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Header("X-Content-Type-Options", "nosniff")
		c.Header("X-Frame-Options", "DENY")
		c.Header("Referrer-Policy", "no-referrer")
		c.Header("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'; base-uri 'none'")
		c.Next()
	}
}
func bodyLimit(n int64) gin.HandlerFunc {
	return func(c *gin.Context) { c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, n); c.Next() }
}
func (a *App) rateLimit() gin.HandlerFunc {
	return func(c *gin.Context) {
		window := time.Minute
		limit := int64(120)
		key := "http:" + c.ClientIP() + ":" + c.Request.URL.Path
		if strings.HasPrefix(c.Request.URL.Path, "/api/v1/auth/") || strings.HasSuffix(c.Request.URL.Path, "/ballots") {
			key = "sensitive:" + c.ClientIP() + ":" + c.Request.URL.Path
			limit = 30
			if strings.Contains(c.Request.URL.Path, "/auth/login") {
				limit = 10
				window = 15 * time.Minute
			}
			if strings.HasSuffix(c.Request.URL.Path, "/ballots") {
				limit = 10
			}
		}
		n, err := redisRateScript.Run(c, a.redis, []string{"rate:" + hash(key)}, window.Milliseconds()).Int64()
		if err != nil {
			fail(c, 503, "Request protection is temporarily unavailable.")
			c.Abort()
			return
		}
		if n > limit {
			fail(c, 429, "Too many requests. Please try again later.")
			c.Abort()
			return
		}
		c.Next()
	}
}
func (a *App) csrf(c *gin.Context) {
	cookie, err := c.Cookie("src_csrf")
	if err != nil || cookie == "" || c.GetHeader("X-CSRF-Token") != cookie {
		fail(c, 403, "Invalid request token.")
		c.Abort()
		return
	}
	c.Next()
}
func fail(c *gin.Context, status int, msg string) {
	c.JSON(status, gin.H{"success": false, "message": msg})
}

// jsonRows responds with a JSON array, never a bare JSON null. A nil Go slice (which Scan leaves
// untouched when a query matches zero rows) marshals to `null`, and every frontend list endpoint
// in this codebase does `response.map(...)` without a null-guard — an election with no positions
// yet, a student with no notifications yet, etc. would otherwise crash the page with "Cannot read
// properties of null (reading 'map')" instead of rendering an empty list.
func jsonRows(c *gin.Context, r []map[string]any) {
	if r == nil {
		r = []map[string]any{}
	}
	c.JSON(200, r)
}
func hash(v string) string { b := sha256.Sum256([]byte(v)); return hex.EncodeToString(b[:]) }
func passwordHash(p string) string {
	salt := make([]byte, 16)
	rand.Read(salt)
	return hex.EncodeToString(salt) + ":" + hex.EncodeToString(argon2.IDKey([]byte(p), salt, 3, 64*1024, 4, 32))
}
func verifyPassword(p, stored string) bool {
	parts := strings.Split(stored, ":")
	if len(parts) != 2 {
		return false
	}
	salt, e := hex.DecodeString(parts[0])
	if e != nil {
		return false
	}
	return hashBytes(argon2.IDKey([]byte(p), salt, 3, 64*1024, 4, 32)) == parts[1]
}
func hashBytes(b []byte) string { return hex.EncodeToString(b) }

func (a *App) login(c *gin.Context) {
	var q loginRequest
	if c.ShouldBindJSON(&q) != nil {
		fail(c, 400, "Invalid credentials.")
		return
	}
	// ID is scanned as a string, not uuid.UUID: GORM's raw-query Scan does not reliably invoke
	// uuid.UUID's own Scan method (it was observed misinterpreting the driver-returned string as
	// per-byte uint8 conversions), so every uuid column in this file is scanned as string and
	// parsed with uuid.Parse instead of scanned directly into a uuid.UUID field.
	var row struct {
		ID           string
		PasswordHash string
		Verified     bool
	}
	if n, err := redisRateScript.Run(c, a.redis, []string{"login-attempt:" + hash(q.RegistrationNumber)}, (15 * time.Minute).Milliseconds()).Int64(); err != nil || n > 10 {
		fail(c, 429, "Too many sign-in attempts. Try again later.")
		return
	}
	if err := a.db.Raw(`SELECT s.id,a.password_hash, a.email_verified AS verified FROM students s JOIN student_accounts a ON a.student_id=s.id WHERE s.registration_number=? AND s.registration_status='REGISTERED' AND s.account_status='ACTIVE'`, q.RegistrationNumber).Scan(&row).Error; err != nil {
		fail(c, 503, "Sign-in is temporarily unavailable.")
		return
	}
	studentID, parseErr := uuid.Parse(row.ID)
	if parseErr != nil || !row.Verified || !verifyPassword(q.Password, row.PasswordHash) {
		a.recordSecurityEvent(c, "LOGIN_FAILED", hash(q.RegistrationNumber), gin.H{"route": c.FullPath()})
		fail(c, 401, "Invalid credentials.")
		return
	}
	a.recordSecurityEvent(c, "LOGIN_SUCCEEDED", hash(q.RegistrationNumber), gin.H{"route": c.FullPath()})
	a.redis.Del(c, "login-attempt:"+hash(q.RegistrationNumber))
	token, err := randomToken(32)
	if err != nil {
		fail(c, 500, "Sign-in could not be completed.")
		return
	}
	if err := a.db.Exec("INSERT INTO sessions(student_id,token_hash,expires_at) VALUES (?,?,?)", studentID, hash(token), time.Now().Add(8*time.Hour)).Error; err != nil {
		fail(c, 503, "Sign-in is temporarily unavailable.")
		return
	}
	csrf, err := randomToken(32)
	if err != nil {
		a.db.Exec("UPDATE sessions SET revoked_at=now() WHERE token_hash=?", hash(token))
		fail(c, 500, "Sign-in could not be completed.")
		return
	}
	setAuthCookies(c, token, csrf)
	c.JSON(200, gin.H{"success": true, "csrf_token": csrf})
}
// cookieSameSite defaults to Strict, matching the specification's same-origin-gateway deployment
// (student-site and the API served through one nginx host). It's only relaxed via COOKIE_SAMESITE
// when the two are deliberately split across different origins/sites (e.g. separate hosted
// services without a shared reverse-proxy origin), in which case browsers never send a Strict or
// even Lax cookie on the cross-site fetches the student-site makes, and every post-login request
// comes back 401. None requires COOKIE_SECURE=true, which is already required for it to apply.
func cookieSameSite() http.SameSite {
	switch strings.ToLower(os.Getenv("COOKIE_SAMESITE")) {
	case "none":
		return http.SameSiteNoneMode
	case "lax":
		return http.SameSiteLaxMode
	default:
		return http.SameSiteStrictMode
	}
}
func setAuthCookies(c *gin.Context, sessionToken, csrfToken string) {
	c.SetSameSite(cookieSameSite())
	c.SetCookie("src_session", sessionToken, 8*3600, "/", "", os.Getenv("COOKIE_SECURE") == "true", true)
	c.SetCookie("src_csrf", csrfToken, 8*3600, "/", "", os.Getenv("COOKIE_SECURE") == "true", false)
}
func clearAuthCookies(c *gin.Context) {
	c.SetSameSite(cookieSameSite())
	c.SetCookie("src_session", "", -1, "/", "", os.Getenv("COOKIE_SECURE") == "true", true)
	c.SetCookie("src_csrf", "", -1, "/", "", os.Getenv("COOKIE_SECURE") == "true", false)
}
func randomToken(size int) (string, error) {
	b := make([]byte, size)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}
func (a *App) auth(c *gin.Context) {
	t, e := c.Cookie("src_session")
	if e != nil {
		fail(c, 401, "Authentication required.")
		c.Abort()
		return
	}
	var row struct{ StudentID string }
	if err := a.db.Raw("SELECT s.student_id FROM sessions s JOIN students u ON u.id=s.student_id JOIN student_accounts a ON a.student_id=s.student_id WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>now() AND u.registration_status='REGISTERED' AND u.account_status='ACTIVE' AND a.email_verified=true", hash(t)).Scan(&row).Error; err != nil {
		fail(c, 503, "Authentication is temporarily unavailable.")
		c.Abort()
		return
	}
	studentID, parseErr := uuid.Parse(row.StudentID)
	if parseErr != nil {
		fail(c, 401, "Authentication required.")
		c.Abort()
		return
	}
	c.Set("principal", principal{studentID})
	c.Next()
}

// This is for the separate Laravel service only, on private networking. It is not a browser admin login.
func (a *App) internalAdmin(c *gin.Context) {
	key := os.Getenv("INTERNAL_ADMIN_API_KEY")
	provided := c.GetHeader("X-Internal-Admin-Key")
	if key == "" || len(provided) != len(key) || subtle.ConstantTimeCompare([]byte(provided), []byte(key)) != 1 {
		fail(c, 401, "Authentication required.")
		c.Abort()
		return
	}
	c.Next()
}
func p(c *gin.Context) principal { return c.MustGet("principal").(principal) }
func (a *App) logout(c *gin.Context) {
	t, _ := c.Cookie("src_session")
	if err := a.db.Exec("UPDATE sessions SET revoked_at=now() WHERE token_hash=?", hash(t)).Error; err != nil {
		fail(c, 503, "Sign-out is temporarily unavailable.")
		return
	}
	clearAuthCookies(c)
	c.Status(204)
}
func (a *App) refresh(c *gin.Context) {
	old, _ := c.Cookie("src_session")
	newToken, err := randomToken(32)
	if err != nil {
		fail(c, 500, "Session refresh could not be completed.")
		return
	}
	if err := a.db.Transaction(func(tx *gorm.DB) error {
		res := tx.Exec("UPDATE sessions SET revoked_at=now() WHERE token_hash=? AND student_id=? AND revoked_at IS NULL AND expires_at>now()", hash(old), p(c).StudentID)
		if res.Error != nil {
			return res.Error
		}
		if res.RowsAffected != 1 {
			return errors.New("session already rotated")
		}
		return tx.Exec("INSERT INTO sessions(student_id,token_hash,expires_at) VALUES (?,?,?)", p(c).StudentID, hash(newToken), time.Now().Add(8*time.Hour)).Error
	}); err != nil {
		fail(c, 503, "Session refresh could not be completed.")
		return
	}
	csrf, err := randomToken(32)
	if err != nil {
		a.db.Exec("UPDATE sessions SET revoked_at=now() WHERE token_hash=?", hash(newToken))
		fail(c, 500, "Session refresh could not be completed.")
		return
	}
	setAuthCookies(c, newToken, csrf)
	c.JSON(200, gin.H{"success": true, "csrf_token": csrf})
}
func (a *App) me(c *gin.Context) {
	var r map[string]any
	a.db.Raw("SELECT registration_number,first_name,last_name,university_email,faculty,programme,level FROM students WHERE id=?", p(c).StudentID).Scan(&r)
	c.JSON(200, r)
}
func (a *App) myEligibility(c *gin.Context) {
	var r []map[string]any
	a.db.Raw(`SELECT e.id,e.name,COALESCE(ve.eligible,false) AS eligible,e.status FROM elections e LEFT JOIN voter_eligibility ve ON ve.election_id=e.id AND ve.student_id=? ORDER BY e.election_date DESC`, p(c).StudentID).Scan(&r)
	jsonRows(c, r)
}
func (a *App) myVotingStatus(c *gin.Context) {
	var r []map[string]any
	a.db.Raw(`SELECT e.id,e.name,COALESCE(vp.has_voted,false) AS has_voted FROM elections e LEFT JOIN voter_participation vp ON vp.election_id=e.id AND vp.student_id=? ORDER BY e.election_date DESC`, p(c).StudentID).Scan(&r)
	jsonRows(c, r)
}
func (a *App) currentElection(c *gin.Context) {
	var r map[string]any
	a.db.Raw("SELECT id,name,election_date,opens_at,closes_at,status,results_visible_from,results_visible_until FROM elections WHERE election_date=(now() AT TIME ZONE ?)::date ORDER BY created_at DESC LIMIT 1", a.loc.String()).Scan(&r)
	if r == nil {
		fail(c, 404, "No current election.")
		return
	}
	c.JSON(200, r)
}
func (a *App) election(c *gin.Context) {
	a.publicOne(c, "SELECT id,name,election_date,opens_at,closes_at,status,results_visible_from,results_visible_until,created_at FROM elections WHERE id=?", c.Param("id"))
}
func (a *App) status(c *gin.Context) {
	var r struct {
		ID                        string
		Name, Status              string
		Opens, Closes             time.Time
		VisibleFrom, VisibleUntil *time.Time
	}
	if err := a.db.Raw("SELECT id,name,status,opens_at AS opens,closes_at AS closes,results_visible_from AS visible_from,results_visible_until AS visible_until FROM elections WHERE id=?", c.Param("id")).Scan(&r).Error; err != nil {
		fail(c, 503, "Election status is unavailable.")
		return
	}
	if r.ID == "" {
		fail(c, 404, "Election not found.")
		return
	}
	now := time.Now().In(a.loc)
	if r.Status == "UPCOMING" && !now.Before(r.Opens.In(a.loc)) && now.Before(r.Closes.In(a.loc)) {
		r.Status = "OPEN"
	}
	if r.Status == "OPEN" && !now.Before(r.Closes.In(a.loc)) {
		r.Status = "CLOSED"
	}
	if r.VisibleUntil != nil && r.Status == "CLOSED" && now.Before(r.VisibleUntil.In(a.loc)) {
		r.Status = "RESULTS_VISIBLE"
	}
	if r.VisibleUntil != nil && !now.Before(r.VisibleUntil.In(a.loc)) && (r.Status == "CLOSED" || r.Status == "RESULTS_VISIBLE") {
		r.Status = "ARCHIVED"
	}
	c.JSON(200, gin.H{"id": r.ID, "name": r.Name, "status": r.Status, "opens_at": r.Opens, "closes_at": r.Closes, "results_visible_from": r.VisibleFrom, "results_visible_until": r.VisibleUntil})
}
func (a *App) publicOne(c *gin.Context, q string, args ...any) {
	var r map[string]any
	a.db.Raw(q, args...).Scan(&r)
	if r == nil {
		fail(c, 404, "Not found.")
		return
	}
	c.JSON(200, r)
}
func (a *App) positions(c *gin.Context) {
	var r []map[string]any
	a.db.Raw("SELECT id,name,description,display_order,max_selections FROM positions WHERE election_id=? ORDER BY display_order", c.Param("id")).Scan(&r)
	jsonRows(c, r)
}
// candidates (and positionCandidates below) order within each position randomly, re-rolled on
// every read, so no candidate gets a structural first-on-the-list advantage across the electorate.
func (a *App) candidates(c *gin.Context) {
	var r []map[string]any
	a.db.Raw("SELECT c.id,c.position_id,c.name,c.photo_url,c.biography,c.manifesto FROM candidates c JOIN positions p ON p.id=c.position_id WHERE p.election_id=? AND c.approval_status='APPROVED' ORDER BY p.display_order,random()", c.Param("id")).Scan(&r)
	jsonRows(c, r)
}
func (a *App) positionCandidates(c *gin.Context) {
	var r []map[string]any
	a.db.Raw("SELECT c.id,c.position_id,c.name,c.photo_url,c.biography,c.manifesto FROM candidates c JOIN positions p ON p.id=c.position_id WHERE p.id=? AND c.approval_status='APPROVED' ORDER BY random()", c.Param("id")).Scan(&r)
	jsonRows(c, r)
}
func (a *App) candidate(c *gin.Context) {
	a.publicOne(c, "SELECT c.id,c.position_id,c.name,c.photo_url,c.biography,c.manifesto FROM candidates c JOIN positions p ON p.id=c.position_id JOIN elections e ON e.id=p.election_id WHERE c.id=? AND c.approval_status='APPROVED'", c.Param("id"))
}
func (a *App) eligibility(c *gin.Context) {
	e, reason := a.canVote(c, p(c).StudentID, c.Param("id"))
	c.JSON(200, gin.H{"eligible": e, "reason": reason})
}
func (a *App) canVote(c *gin.Context, student uuid.UUID, election string) (bool, string) {
	var x struct {
		Verified, Eligible, HasVoted bool
		Status                       string
		Opens, Closes                time.Time
	}
	// opens_at/closes_at must be aliased to match the struct field names (Opens/Closes) — GORM
	// matches raw-query columns to struct fields by name, and e.opens_at/e.closes_at without an
	// alias do not match Opens/Closes, so those fields silently stayed at their zero value and
	// every eligibility check failed the time-window test regardless of the real election times.
	if err := a.db.Raw(`SELECT a.email_verified AS verified, COALESCE(ve.eligible,false) AS eligible, COALESCE(vp.has_voted,false) AS has_voted,e.status,e.opens_at AS opens,e.closes_at AS closes FROM students s JOIN student_accounts a ON a.student_id=s.id JOIN elections e ON e.id=? LEFT JOIN voter_eligibility ve ON ve.student_id=s.id AND ve.election_id=e.id LEFT JOIN voter_participation vp ON vp.student_id=s.id AND vp.election_id=e.id WHERE s.id=? AND s.registration_status='REGISTERED' AND s.account_status='ACTIVE'`, election, student).Scan(&x).Error; err != nil {
		return false, "Voting eligibility could not be verified."
	}
	if !x.Verified {
		return false, "Verify your university email before voting."
	}
	if !x.Eligible {
		return false, "You are not eligible to vote in this SRC election."
	}
	now := time.Now()
	if now.Before(x.Opens) || !now.Before(x.Closes) {
		return false, "Voting is not currently open."
	}
	if x.Status != "OPEN" {
		return false, "Voting is not currently open."
	}
	if x.HasVoted {
		return false, "You have already voted in this election."
	}
	return true, ""
}
func (a *App) ballot(c *gin.Context) {
	if ok, msg := a.canVote(c, p(c).StudentID, c.Param("id")); !ok {
		fail(c, 403, msg)
		return
	}
	a.positions(c)
}
func (a *App) submitBallot(c *gin.Context) {
	var q ballotRequest
	if c.ShouldBindJSON(&q) != nil {
		fail(c, 400, "Invalid ballot.")
		return
	}
	eID, err := uuid.Parse(c.Param("id"))
	if err != nil {
		fail(c, 404, "Election not found.")
		return
	}
	student := p(c).StudentID
	receipt := ""
	err = a.db.Transaction(func(tx *gorm.DB) error {
		lockID := int64(binary.BigEndian.Uint64(eID[:8]) ^ binary.BigEndian.Uint64(student[:8]))
		if err := tx.Exec("SELECT pg_advisory_xact_lock(?)", lockID).Error; err != nil {
			return err
		}
		var election struct{ Status string }
		if e := tx.Raw("SELECT status FROM elections WHERE id=? AND status='OPEN' AND now() >= opens_at AND now() < closes_at FOR UPDATE", eID).Scan(&election).Error; e != nil {
			return e
		} else if election.Status != "OPEN" {
			return errors.New("not open")
		}
		var account struct {
			Verified, Eligible, HasVoted bool
			ParticipationID              string
		}
		if err := tx.Raw(`SELECT a.email_verified AS verified,ve.eligible,COALESCE(vp.has_voted,false) AS has_voted,vp.id AS participation_id FROM student_accounts a JOIN students s ON s.id=a.student_id JOIN voter_eligibility ve ON ve.student_id=a.student_id AND ve.election_id=? LEFT JOIN voter_participation vp ON vp.student_id=a.student_id AND vp.election_id=? WHERE a.student_id=? AND s.registration_status='REGISTERED' AND s.account_status='ACTIVE' FOR UPDATE OF a`, eID, eID, student).Scan(&account).Error; err != nil {
			return err
		}
		if !account.Verified || !account.Eligible || account.HasVoted {
			return errors.New("ineligible")
		}
		participationID, parseErr := uuid.Parse(account.ParticipationID)
		if parseErr != nil {
			participationID = uuid.New()
			if e := tx.Exec("INSERT INTO voter_participation(id,student_id,election_id) VALUES (?,?,?)", participationID, student, eID).Error; e != nil {
				return errors.New("already voted")
			}
		}
		counts := map[string]int{}
		seen := map[string]bool{}
		for _, s := range q.Selections {
			if seen[s.PositionID+":"+s.CandidateID] {
				return errors.New("duplicate candidate selection")
			}
			seen[s.PositionID+":"+s.CandidateID] = true
			counts[s.PositionID]++
			var limit int
			if err := tx.Raw("SELECT max_selections FROM positions WHERE id=? AND election_id=?", s.PositionID, eID).Scan(&limit).Error; err != nil {
				return err
			}
			if limit == 0 || counts[s.PositionID] > limit {
				return errors.New("selection limit")
			}
			var valid bool
			if err := tx.Raw(`SELECT EXISTS(SELECT 1 FROM candidates c JOIN positions p ON p.id=c.position_id WHERE c.id=? AND p.id=? AND p.election_id=? AND c.approval_status='APPROVED')`, s.CandidateID, s.PositionID, eID).Scan(&valid).Error; err != nil {
				return err
			}
			if !valid {
				return errors.New("invalid candidate")
			}
		}
		ballot := uuid.New()
		if e := tx.Exec("INSERT INTO ballots(id,election_id) VALUES (?,?)", ballot, eID).Error; e != nil {
			return e
		}
		for _, s := range q.Selections {
			if e := tx.Exec("INSERT INTO ballot_selections(ballot_id,position_id,candidate_id) VALUES (?,?,?)", ballot, s.PositionID, s.CandidateID).Error; e != nil {
				return e
			}
		}
		if e := tx.Exec("UPDATE voter_participation SET has_voted=true,voted_at=now() WHERE id=? AND has_voted=false", participationID).Error; e != nil {
			return e
		}
		raw := newReceipt()
		if e := tx.Exec("INSERT INTO voting_receipts(participation_id,reference,reference_hash) VALUES (?,?,?)", participationID, raw, hash(raw)).Error; e != nil {
			return e
		}
		if err := a.appendAudit(tx, "student:"+student.String(), "BALLOT_ACCEPTED", "election:"+eID.String(), c.GetHeader("X-Request-ID")); err != nil {
			return err
		}
		if e := tx.Exec("INSERT INTO notifications(student_id,type,message) VALUES (?,?,?)", student, "BALLOT_SUBMITTED", "Your ballot has been successfully submitted.").Error; e != nil {
			return e
		}
		receipt = raw
		return nil
	})
	if err != nil {
		fail(c, 409, "Your ballot could not be accepted. It may already have been submitted.")
		return
	}
	c.JSON(201, gin.H{"success": true, "receipt_reference": receipt, "message": "Ballot successfully submitted."})
}
func newReceipt() string {
	b := make([]byte, 5)
	rand.Read(b)
	return "SRC-" + strings.ToUpper(hex.EncodeToString(b[:2])) + "-" + strings.ToUpper(hex.EncodeToString(b[2:]))
}
func (a *App) receipt(c *gin.Context) {
	var r struct {
		Reference string
	}
	a.db.Raw(`SELECT vr.reference FROM voting_receipts vr JOIN voter_participation vp ON vp.id=vr.participation_id WHERE vp.student_id=? AND vp.election_id=? AND vp.has_voted=true`, p(c).StudentID, c.Param("id")).Scan(&r)
	if r.Reference == "" {
		fail(c, 404, "No receipt found.")
		return
	}
	c.JSON(200, gin.H{"status": "accepted", "reference": r.Reference, "message": "Your ballot was successfully recorded."})
}
func (a *App) turnout(c *gin.Context) {
	var state string
	if err := a.db.Raw("SELECT status FROM elections WHERE id=?", c.Param("id")).Scan(&state).Error; err != nil || state == "" {
		fail(c, 404, "Election not found.")
		return
	}
	if state == "OPEN" && c.FullPath() == "/api/v1/elections/:id/turnout" {
		fail(c, 404, "Turnout is not currently available.")
		return
	}
	var r struct{ Eligible, Votes int64 }
	a.db.Raw(`SELECT count(*) FILTER(WHERE ve.eligible) eligible,count(*) FILTER(WHERE vp.has_voted) votes FROM voter_eligibility ve LEFT JOIN voter_participation vp ON vp.student_id=ve.student_id AND vp.election_id=ve.election_id WHERE ve.election_id=?`, c.Param("id")).Scan(&r)
	c.JSON(200, gin.H{"eligible_students": r.Eligible, "votes_cast": r.Votes, "turnout_percent": pct(r.Votes, r.Eligible)})
}
func pct(a, b int64) float64 {
	if b == 0 {
		return 0
	}
	return float64(a) * 100 / float64(b)
}
// results is the student-facing read: sealed totals only become visible once staff have published
// them (a separate, equally immutable, insert-only result_publications row) -- being SEALED alone
// is not enough. See migrations/007_result_publication.sql.
func (a *App) results(c *gin.Context) {
	var r struct {
		Totals       json.RawMessage
		Hash         string
		VisibleUntil time.Time
	}
	if err := a.db.Raw(`SELECT rb.totals,rb.result_hash AS hash,rp.visible_until FROM result_batches rb JOIN result_publications rp ON rp.election_id=rb.election_id WHERE rb.election_id=? AND rb.status='SEALED' AND rp.visible_until>now()`, c.Param("id")).Scan(&r).Error; err != nil {
		fail(c, 503, "Results are temporarily unavailable.")
		return
	}
	if len(r.Totals) == 0 {
		fail(c, 404, "Results are not currently available.")
		return
	}
	c.JSON(200, gin.H{"status": "SEALED", "totals": json.RawMessage(r.Totals), "integrity_hash": r.Hash, "visible_until": r.VisibleUntil})
}

// adminResults lets staff review the sealed totals and charts as soon as the election closes --
// before publishing, not gated by whether a result_publications row exists yet -- so there's a
// real review step rather than results reaching students the instant voting closes.
func (a *App) adminResults(c *gin.Context) {
	var r struct {
		Totals      json.RawMessage
		Hash        string
		GeneratedAt time.Time
		Published   bool
		PublishedAt *time.Time
	}
	if err := a.db.Raw(`SELECT rb.totals,rb.result_hash AS hash,rb.generated_at,(rp.election_id IS NOT NULL) AS published,rp.published_at FROM result_batches rb LEFT JOIN result_publications rp ON rp.election_id=rb.election_id WHERE rb.election_id=? AND rb.status='SEALED'`, c.Param("id")).Scan(&r).Error; err != nil {
		fail(c, 503, "Results are temporarily unavailable.")
		return
	}
	if len(r.Totals) == 0 {
		fail(c, 404, "Results are not available. The election may not have closed yet.")
		return
	}
	c.JSON(200, gin.H{"status": "SEALED", "totals": json.RawMessage(r.Totals), "integrity_hash": r.Hash, "generated_at": r.GeneratedAt, "published": r.Published, "published_at": r.PublishedAt})
}

// adminPublishResults is the one-time, irreversible act of revealing already-sealed results to
// students. A second insert-only row rather than an update to result_batches (which cannot be
// updated at all once inserted) -- publishing is itself tamper-evident, not just the tally.
func (a *App) adminPublishResults(c *gin.Context) {
	eID := c.Param("id")
	var sealed bool
	if err := a.db.Raw("SELECT EXISTS(SELECT 1 FROM result_batches WHERE election_id=? AND status='SEALED')", eID).Scan(&sealed).Error; err != nil || !sealed {
		fail(c, 409, "Results cannot be published yet. The election must be closed and sealed first.")
		return
	}
	until := time.Now().UTC().Add(24 * time.Hour)
	res := a.db.Exec("INSERT INTO result_publications(election_id,visible_until) VALUES (?,?) ON CONFLICT (election_id) DO NOTHING", eID, until)
	if res.Error != nil {
		fail(c, 500, "Results could not be published.")
		return
	}
	if res.RowsAffected == 0 {
		fail(c, 409, "Results have already been published and are locked.")
		return
	}
	a.db.Exec("UPDATE elections SET status='RESULTS_VISIBLE' WHERE id=? AND status='CLOSED'", eID)
	if err := a.broadcastElectionNotification(a.db, uuid.MustParse(eID), "RESULTS_AVAILABLE", "The SRC election has closed. Official sealed results are now available for the next 24 hours."); err != nil {
		fail(c, 500, "Results were published but notifications could not be sent.")
		return
	}
	if err := a.appendAudit(a.db, "internal-admin-api", "RESULT_PUBLISHED", "election:"+eID, ""); err != nil {
		fail(c, 500, "Results were published but the audit log entry failed.")
		return
	}
	c.JSON(200, gin.H{"published": true, "visible_until": until})
}
func (a *App) adminTiming(c *gin.Context) {
	var r map[string]any
	a.db.Raw("SELECT opens_at,closes_at,results_visible_from,results_visible_until,status FROM elections WHERE id=?", c.Param("id")).Scan(&r)
	if r == nil {
		fail(c, 404, "Election not found.")
		return
	}
	c.JSON(200, r)
}
// The following handlers are reachable only behind a.internalAdmin (a private service-to-service
// key, never a browser session). They are how the separate Laravel election-management portal
// configures an election; PostgreSQL triggers (migrations 001-003) independently enforce that
// positions, candidates, election timing and voter eligibility all freeze once voting opens, so
// these handlers do not need to duplicate that lock in application code.
type adminElectionRequest struct {
	Name         string `json:"name" binding:"required,max=200"`
	ElectionDate string `json:"election_date" binding:"required"`
}

func (a *App) adminCreateElection(c *gin.Context) {
	var q adminElectionRequest
	if c.ShouldBindJSON(&q) != nil {
		fail(c, 400, "Invalid election details.")
		return
	}
	day, err := time.ParseInLocation("2006-01-02", q.ElectionDate, a.loc)
	if err != nil {
		fail(c, 400, "election_date must be in YYYY-MM-DD format.")
		return
	}
	opens := time.Date(day.Year(), day.Month(), day.Day(), 6, 0, 0, 0, a.loc)
	closes := time.Date(day.Year(), day.Month(), day.Day(), 23, 59, 0, 0, a.loc)
	id := uuid.New()
	if err := a.db.Exec("INSERT INTO elections(id,name,election_date,opens_at,closes_at) VALUES (?,?,?,?,?)", id, q.Name, day.Format("2006-01-02"), opens, closes).Error; err != nil {
		fail(c, 400, "Election could not be created.")
		return
	}
	c.JSON(201, gin.H{"id": id, "name": q.Name, "election_date": day.Format("2006-01-02"), "opens_at": opens, "closes_at": closes})
}

// adminUpdateElection lets staff correct a name/date typo before an election opens. The existing
// election_timing_locked trigger (migration 001) rejects this once the election leaves UPCOMING,
// so there is nothing further to check here.
func (a *App) adminUpdateElection(c *gin.Context) {
	var q adminElectionRequest
	if c.ShouldBindJSON(&q) != nil {
		fail(c, 400, "Invalid election details.")
		return
	}
	day, err := time.ParseInLocation("2006-01-02", q.ElectionDate, a.loc)
	if err != nil {
		fail(c, 400, "election_date must be in YYYY-MM-DD format.")
		return
	}
	opens := time.Date(day.Year(), day.Month(), day.Day(), 6, 0, 0, 0, a.loc)
	closes := time.Date(day.Year(), day.Month(), day.Day(), 23, 59, 0, 0, a.loc)
	res := a.db.Exec("UPDATE elections SET name=?,election_date=?,opens_at=?,closes_at=? WHERE id=?", q.Name, day.Format("2006-01-02"), opens, closes, c.Param("id"))
	if res.Error != nil {
		fail(c, 409, "Election could not be updated. It may already be open.")
		return
	}
	if res.RowsAffected == 0 {
		fail(c, 404, "Election not found.")
		return
	}
	c.JSON(200, gin.H{"id": c.Param("id"), "name": q.Name, "election_date": day.Format("2006-01-02"), "opens_at": opens, "closes_at": closes})
}

type adminPositionRequest struct {
	Name          string `json:"name" binding:"required,max=120"`
	Description   string `json:"description"`
	DisplayOrder  int    `json:"display_order" binding:"required,min=1"`
	MaxSelections int    `json:"max_selections" binding:"required,min=1"`
}

func (a *App) adminCreatePosition(c *gin.Context) {
	var q adminPositionRequest
	if c.ShouldBindJSON(&q) != nil {
		fail(c, 400, "Invalid position details.")
		return
	}
	id := uuid.New()
	if err := a.db.Exec("INSERT INTO positions(id,election_id,name,description,display_order,max_selections) VALUES (?,?,?,?,?,?)", id, c.Param("id"), q.Name, q.Description, q.DisplayOrder, q.MaxSelections).Error; err != nil {
		fail(c, 409, "Position could not be created. The election may already be open, or the name/order may be in use.")
		return
	}
	c.JSON(201, gin.H{"id": id, "election_id": c.Param("id"), "name": q.Name, "description": q.Description, "display_order": q.DisplayOrder, "max_selections": q.MaxSelections})
}

func (a *App) adminUpdatePosition(c *gin.Context) {
	var q adminPositionRequest
	if c.ShouldBindJSON(&q) != nil {
		fail(c, 400, "Invalid position details.")
		return
	}
	res := a.db.Exec("UPDATE positions SET name=?,description=?,display_order=?,max_selections=? WHERE id=?", q.Name, q.Description, q.DisplayOrder, q.MaxSelections, c.Param("id"))
	if res.Error != nil {
		fail(c, 409, "Position could not be updated. The election may already be open.")
		return
	}
	if res.RowsAffected == 0 {
		fail(c, 404, "Position not found.")
		return
	}
	c.Status(204)
}

func (a *App) adminDeletePosition(c *gin.Context) {
	res := a.db.Exec("DELETE FROM positions WHERE id=?", c.Param("id"))
	if res.Error != nil {
		fail(c, 409, "Position could not be removed. The election may already be open.")
		return
	}
	if res.RowsAffected == 0 {
		fail(c, 404, "Position not found.")
		return
	}
	c.Status(204)
}

type adminCandidateRequest struct {
	Name      string `json:"name" binding:"required,max=200"`
	PhotoURL  string `json:"photo_url"`
	Biography string `json:"biography"`
	Manifesto string `json:"manifesto"`
}

func (a *App) adminCreateCandidate(c *gin.Context) {
	var q adminCandidateRequest
	if c.ShouldBindJSON(&q) != nil {
		fail(c, 400, "Invalid candidate details.")
		return
	}
	id := uuid.New()
	if err := a.db.Exec("INSERT INTO candidates(id,position_id,name,photo_url,biography,manifesto) VALUES (?,?,?,?,?,?)", id, c.Param("id"), q.Name, q.PhotoURL, q.Biography, q.Manifesto).Error; err != nil {
		fail(c, 409, "Candidate could not be created. The election may already be open.")
		return
	}
	c.JSON(201, gin.H{"id": id, "position_id": c.Param("id"), "name": q.Name, "approval_status": "PENDING"})
}

func (a *App) adminUpdateCandidate(c *gin.Context) {
	var q adminCandidateRequest
	if c.ShouldBindJSON(&q) != nil {
		fail(c, 400, "Invalid candidate details.")
		return
	}
	res := a.db.Exec("UPDATE candidates SET name=?,photo_url=?,biography=?,manifesto=? WHERE id=?", q.Name, q.PhotoURL, q.Biography, q.Manifesto, c.Param("id"))
	if res.Error != nil {
		fail(c, 409, "Candidate could not be updated. The election may already be open.")
		return
	}
	if res.RowsAffected == 0 {
		fail(c, 404, "Candidate not found.")
		return
	}
	c.Status(204)
}

// adminSetCandidateApproval returns a handler bound to one fixed status so the route table stays
// declarative; it is never called with a status value that did not come from this file.
func (a *App) adminSetCandidateApproval(status string) gin.HandlerFunc {
	return func(c *gin.Context) {
		res := a.db.Exec("UPDATE candidates SET approval_status=? WHERE id=?", status, c.Param("id"))
		if res.Error != nil {
			fail(c, 409, "Candidate status could not be changed. The election may already be open.")
			return
		}
		if res.RowsAffected == 0 {
			fail(c, 404, "Candidate not found.")
			return
		}
		c.Status(204)
	}
}

type eligibilityEntry struct {
	RegistrationNumber string `json:"registration_number" binding:"required,max=64"`
	Eligible           bool   `json:"eligible"`
}
type adminEligibilityRequest struct {
	Entries []eligibilityEntry `json:"entries" binding:"required,min=1,max=5000"`
}

func (a *App) adminSetEligibility(c *gin.Context) {
	var q adminEligibilityRequest
	if c.ShouldBindJSON(&q) != nil {
		fail(c, 400, "Invalid eligibility list.")
		return
	}
	eID := c.Param("id")
	failed := []gin.H{}
	updated := 0
	for _, entry := range q.Entries {
		res := a.db.Exec(`INSERT INTO voter_eligibility(student_id,election_id,eligible,verified_at) SELECT id,?,?,now() FROM students WHERE registration_number=? ON CONFLICT (student_id,election_id) DO UPDATE SET eligible=EXCLUDED.eligible,verified_at=EXCLUDED.verified_at`, eID, entry.Eligible, entry.RegistrationNumber)
		if res.Error != nil {
			failed = append(failed, gin.H{"registration_number": entry.RegistrationNumber, "reason": "rejected, election may already be open"})
			continue
		}
		if res.RowsAffected == 0 {
			failed = append(failed, gin.H{"registration_number": entry.RegistrationNumber, "reason": "student not found"})
			continue
		}
		updated++
	}
	c.JSON(200, gin.H{"updated": updated, "failed": failed})
}

type studentImportEntry struct {
	RegistrationNumber string `json:"registration_number" binding:"required,max=64"`
	FirstName          string `json:"first_name" binding:"required,max=120"`
	LastName           string `json:"last_name" binding:"required,max=120"`
	UniversityEmail    string `json:"university_email" binding:"required,email,max=254"`
	NationalID         string `json:"national_id" binding:"required,min=4,max=64"`
	Faculty            string `json:"faculty"`
	Programme          string `json:"programme"`
	Level              string `json:"level"`
}
type adminStudentImportRequest struct {
	Students []studentImportEntry `json:"students" binding:"required,min=1,max=5000"`
}

// adminImportStudents loads the official, currently-registered-and-active student population and
// provisions each student's login credential in the same step: registration_number is the
// username, national_id is the password. There is no separate self-registration/email-OTP flow —
// a student can sign in the moment the university imports them here. Re-importing a student
// (e.g. to correct a typo) refreshes their password to match the newly supplied national_id.
//
// The students table's CHECK constraints only ever permit REGISTERED/ACTIVE rows (migration 001),
// so a student who leaves that population is removed by deleting their row, not by flipping a flag.
func (a *App) adminImportStudents(c *gin.Context) {
	var q adminStudentImportRequest
	if c.ShouldBindJSON(&q) != nil {
		fail(c, 400, "Invalid student import payload.")
		return
	}
	failed := []gin.H{}
	upserted := 0
	for _, s := range q.Students {
		email := strings.ToLower(strings.TrimSpace(s.UniversityEmail))
		if !strings.HasSuffix(email, "@"+a.domain) {
			failed = append(failed, gin.H{"registration_number": s.RegistrationNumber, "reason": "email is not on the approved student domain"})
			continue
		}
		err := a.db.Transaction(func(tx *gorm.DB) error {
			var student struct{ ID string }
			if err := tx.Raw(`INSERT INTO students(registration_number,first_name,last_name,university_email,faculty,programme,level,registration_status,account_status)
				VALUES (?,?,?,?,?,?,?,'REGISTERED','ACTIVE')
				ON CONFLICT (registration_number) DO UPDATE SET first_name=EXCLUDED.first_name,last_name=EXCLUDED.last_name,university_email=EXCLUDED.university_email,faculty=EXCLUDED.faculty,programme=EXCLUDED.programme,level=EXCLUDED.level
				RETURNING id`,
				s.RegistrationNumber, s.FirstName, s.LastName, email, s.Faculty, s.Programme, s.Level).Scan(&student).Error; err != nil {
				return err
			}
			return tx.Exec(`INSERT INTO student_accounts(student_id,password_hash,email_verified,email_verified_at) VALUES (?,?,true,now())
				ON CONFLICT (student_id) DO UPDATE SET password_hash=EXCLUDED.password_hash,email_verified=true,email_verified_at=now()`,
				student.ID, passwordHash(s.NationalID)).Error
		})
		if err != nil {
			failed = append(failed, gin.H{"registration_number": s.RegistrationNumber, "reason": "rejected, likely a duplicate email"})
			continue
		}
		upserted++
	}
	c.JSON(200, gin.H{"upserted": upserted, "failed": failed})
}

func (a *App) adminListElections(c *gin.Context) {
	var r []map[string]any
	a.db.Raw("SELECT id,name,election_date,opens_at,closes_at,status,results_visible_from,results_visible_until FROM elections ORDER BY election_date DESC").Scan(&r)
	jsonRows(c, r)
}

// adminCandidates returns every candidate regardless of approval_status so staff can review and
// decide on ones still PENDING; the public candidates()/positionCandidates() handlers deliberately
// only ever show APPROVED candidates to students.
func (a *App) adminCandidates(c *gin.Context) {
	var r []map[string]any
	a.db.Raw("SELECT c.id,c.position_id,c.name,c.photo_url,c.biography,c.manifesto,c.approval_status FROM candidates c JOIN positions p ON p.id=c.position_id WHERE p.election_id=? ORDER BY p.display_order,c.name", c.Param("id")).Scan(&r)
	jsonRows(c, r)
}

func (a *App) audit(c *gin.Context) {
	var r []map[string]any
	if err := a.db.Raw("SELECT id,actor,action,resource,request_id,previous_hash,entry_hash,created_at FROM audit_logs ORDER BY id DESC LIMIT 200").Scan(&r).Error; err != nil {
		fail(c, 503, "Audit records are temporarily unavailable.")
		return
	}
	jsonRows(c, r)
}
func (a *App) appendAudit(tx *gorm.DB, actor, action, resource, requestID string) error {
	if err := tx.Exec("SELECT pg_advisory_xact_lock(?)", int64(0x5352434155444954)).Error; err != nil {
		return err
	}
	var previous string
	if err := tx.Raw("SELECT entry_hash FROM audit_logs ORDER BY id DESC LIMIT 1").Scan(&previous).Error; err != nil {
		return err
	}
	created := time.Now().UTC()
	material := strings.Join([]string{previous, actor, action, resource, requestID, created.Format(time.RFC3339Nano)}, "\n")
	entryHash := hash(material)
	return tx.Exec("INSERT INTO audit_logs(actor,action,resource,request_id,previous_hash,entry_hash,created_at) VALUES (?,?,?,?,?,?,?)", actor, action, resource, requestID, previous, entryHash, created).Error
}
func (a *App) recordSecurityEvent(c *gin.Context, category, subject string, details gin.H) {
	encoded, err := json.Marshal(details)
	if err != nil {
		return
	}
	if err := a.db.Exec("SELECT append_security_event(?,?,?::jsonb)", category, subject, string(encoded)).Error; err != nil {
		slog.Error("security event could not be stored", "category", category, "error", err)
	}
}
func (a *App) notifications(c *gin.Context) {
	var r []map[string]any
	a.db.Raw("SELECT id,type,message,read_at,created_at FROM notifications WHERE student_id=? ORDER BY created_at DESC", p(c).StudentID).Scan(&r)
	jsonRows(c, r)
}
func (a *App) readNotification(c *gin.Context) {
	a.db.Exec("UPDATE notifications SET read_at=now() WHERE id=? AND student_id=?", c.Param("id"), p(c).StudentID)
	c.Status(204)
}
func (a *App) dashboard(c *gin.Context) {
	student := p(c).StudentID
	var profile map[string]any
	a.db.Raw("SELECT first_name,last_name,registration_number,faculty,programme,level FROM students WHERE id=?", student).Scan(&profile)
	// Prefer the election the student can actually act on right now: one they're eligible for
	// outranks one they're not (relevant when more than one election is live at once), and within
	// that an OPEN one outranks a later-dated UPCOMING one. Picking "latest election_date"
	// unconditionally (the previous behavior) meant a far-future scheduled election always hid an
	// already-open one on the dashboard, so a student could never reach a real ballot while both
	// existed.
	const relevantElection = `elections WHERE election_date>=current_date-1 ORDER BY (CASE status WHEN 'OPEN' THEN 0 WHEN 'RESULTS_VISIBLE' THEN 1 WHEN 'UPCOMING' THEN 2 ELSE 3 END), election_date ASC, created_at DESC LIMIT 1`
	var election map[string]any
	a.db.Raw(`SELECT e.id,e.name,e.status,e.opens_at,e.closes_at,e.results_visible_from,e.results_visible_until,COALESCE(vp.has_voted,false) AS has_voted FROM elections e LEFT JOIN voter_participation vp ON vp.election_id=e.id AND vp.student_id=? LEFT JOIN voter_eligibility ve ON ve.election_id=e.id AND ve.student_id=? WHERE e.election_date>=current_date-1 ORDER BY (CASE WHEN ve.eligible THEN 0 ELSE 1 END), (CASE e.status WHEN 'OPEN' THEN 0 WHEN 'RESULTS_VISIBLE' THEN 1 WHEN 'UPCOMING' THEN 2 ELSE 3 END), e.election_date ASC, e.created_at DESC LIMIT 1`, student, student).Scan(&election)
	var notes []map[string]any
	a.db.Raw("SELECT id,type,message,read_at,created_at FROM notifications WHERE student_id=? ORDER BY created_at DESC LIMIT 10", student).Scan(&notes)
	var resultsAvailable bool
	a.db.Raw(`SELECT EXISTS(SELECT 1 FROM result_batches rb JOIN result_publications rp ON rp.election_id=rb.election_id WHERE rb.election_id=(SELECT id FROM `+relevantElection+`) AND rb.status='SEALED' AND rp.visible_until>now())`).Scan(&resultsAvailable)
	if notes == nil {
		notes = []map[string]any{}
	}
	c.JSON(200, gin.H{"student": profile, "election": election, "notifications": notes, "results_available": resultsAvailable})
}

// The server clock is authoritative. This worker can run in every API instance: row locks and
// unique result_batches(election_id) make closure/counting safe under horizontal scaling.
func (a *App) electionEngine() {
	tick := time.NewTicker(30 * time.Second)
	defer tick.Stop()
	for {
		if err := a.advanceElections(); err != nil {
			slog.Error("election lifecycle advancement failed", "error", err)
		}
		<-tick.C
	}
}
func (a *App) advanceElections() error {
	now := time.Now().In(a.loc)
	var es []struct {
		ID            string
		Status        string
		Opens, Closes time.Time
	}
	if err := a.db.Raw("SELECT id,status,opens_at AS opens,closes_at AS closes FROM elections WHERE status IN ('UPCOMING','OPEN','CLOSED')").Scan(&es).Error; err != nil {
		return err
	}
	for _, e := range es {
		electionID, err := uuid.Parse(e.ID)
		if err != nil {
			return fmt.Errorf("election %s has an invalid id: %w", e.ID, err)
		}
		localOpen := e.Opens.In(a.loc)
		localClose := e.Closes.In(a.loc)
		if localOpen.Hour() != 6 || localOpen.Minute() != 0 || localOpen.Second() != 0 || localClose.Hour() != 23 || localClose.Minute() != 59 || localClose.Second() != 0 || localOpen.Year() != localClose.Year() || localOpen.YearDay() != localClose.YearDay() {
			return fmt.Errorf("election %s schedule violates 06:00-23:59 university-local policy", e.ID)
		}
		if e.Status == "UPCOMING" && !now.Before(e.Opens) && now.Before(e.Closes) {
			// A candidate staff never approved or rejected before voting opened auto-loses rather
			// than silently appearing or vanishing later: it's moved to REJECTED (not DISQUALIFIED,
			// which is reserved for staff action against a candidate that was already on the
			// ballot) so it never appears on the ballot. Runs while the election is still UPCOMING,
			// before the status flip below, so the normal (not open-election) trigger path applies.
			if err := a.db.Exec(`UPDATE candidates SET approval_status='REJECTED' WHERE approval_status='PENDING' AND position_id IN (SELECT id FROM positions WHERE election_id=?)`, electionID).Error; err != nil {
				return err
			}
			res := a.db.Exec("UPDATE elections SET status='OPEN' WHERE id=? AND status='UPCOMING'", electionID)
			if res.Error != nil {
				return res.Error
			}
			// RowsAffected is 0 if another API instance already made this same transition; only the
			// instance that actually flipped the status should notify, so students get one message.
			if res.RowsAffected > 0 {
				if err := a.broadcastElectionNotification(a.db, electionID, "ELECTION_OPEN", "SRC election voting is now open."); err != nil {
					return err
				}
			}
		}
		if (e.Status == "OPEN" || e.Status == "UPCOMING") && !now.Before(e.Closes) {
			if err := a.db.Transaction(func(tx *gorm.DB) error {
				var current struct{ Status string }
				if err := tx.Raw("SELECT status FROM elections WHERE id=? FOR UPDATE", electionID).Scan(&current).Error; err != nil {
					return err
				}
				if current.Status != "OPEN" && current.Status != "UPCOMING" {
					return nil
				}
				if err := tx.Exec("UPDATE elections SET status='CLOSED' WHERE id=? AND status IN ('OPEN','UPCOMING')", electionID).Error; err != nil {
					return err
				}
				return a.sealResults(tx, electionID, now.In(time.UTC))
			}); err != nil {
				return err
			}
		}
	}
	return nil
}
func (a *App) sealResults(tx *gorm.DB, election uuid.UUID, now time.Time) error {
	var existing int64
	if err := tx.Raw("SELECT count(*) FROM result_batches WHERE election_id=?", election).Scan(&existing).Error; err != nil {
		return err
	}
	if existing > 0 {
		return nil
	}
	var rows []struct {
		PositionID, CandidateID string
		Position, Candidate     string
		Votes                   int64
	}
	// A candidate disqualified mid-election keeps every ballot selection cast for them (ballots are
	// immutable and anonymous), but their votes are excluded from the sealed, public tally here.
	tx.Raw(`SELECT bs.position_id,bs.candidate_id,p.name AS position,c.name AS candidate,count(*) AS votes FROM ballot_selections bs JOIN positions p ON p.id=bs.position_id JOIN candidates c ON c.id=bs.candidate_id WHERE p.election_id=? AND c.approval_status<>'DISQUALIFIED' GROUP BY bs.position_id,bs.candidate_id,p.name,c.name ORDER BY p.name,c.name`, election).Scan(&rows)
	data, err := json.Marshal(rows)
	if err != nil {
		return err
	}
	sum := sha256.Sum256(data)
	until := now.UTC().Add(24 * time.Hour)
	// visible_from/visible_until are no longer what gates student visibility (see
	// migrations/007_result_publication.sql) -- kept here only as a record of when sealing
	// happened, not acted on by any read path.
	if err := tx.Exec("INSERT INTO result_batches(election_id,totals,result_hash,status,visible_from,visible_until) VALUES (?,?,?,?,?,?)", election, string(data), hex.EncodeToString(sum[:]), "SEALED", now.UTC(), until).Error; err != nil {
		return err
	}
	// Sealing freezes the tamper-proof tally the instant voting closes; it does not reveal
	// anything to students. That only happens when staff explicitly publish (adminPublishResults),
	// so no student notification fires here.
	return a.appendAudit(tx, "election-engine", "RESULT_SEALED", "election:"+election.String(), "")
}

// broadcastElectionNotification notifies every student on that election's eligibility register.
// It is deliberately keyed off voter_eligibility rather than voter_participation: unlike a ballot
// choice, "voting is open" / "results are available" carries no secrecy risk tied to student_id.
func (a *App) broadcastElectionNotification(tx *gorm.DB, election uuid.UUID, kind, message string) error {
	return tx.Exec(`INSERT INTO notifications(student_id,type,message) SELECT student_id,?,? FROM voter_eligibility WHERE election_id=? AND eligible=true`, kind, message, election).Error
}
