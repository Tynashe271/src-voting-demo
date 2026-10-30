package main

import (
	"strings"
	"testing"
)

func TestPasswordHashRoundTrip(t *testing.T) {
	stored := passwordHash("correct horse battery staple")
	if !verifyPassword("correct horse battery staple", stored) {
		t.Fatal("verifyPassword rejected the password it was hashed from")
	}
	if verifyPassword("wrong password", stored) {
		t.Fatal("verifyPassword accepted an incorrect password")
	}
}

func TestPasswordHashUsesDistinctSalts(t *testing.T) {
	a := passwordHash("same password")
	b := passwordHash("same password")
	if a == b {
		t.Fatal("two hashes of the same password must not be identical (salt reuse)")
	}
}

func TestVerifyPasswordRejectsMalformedHash(t *testing.T) {
	if verifyPassword("anything", "not-a-valid-stored-hash") {
		t.Fatal("verifyPassword must fail closed on a malformed stored hash")
	}
}

func TestNewReceiptFormat(t *testing.T) {
	r := newReceipt()
	if !strings.HasPrefix(r, "SRC-") {
		t.Fatalf("receipt %q missing SRC- prefix", r)
	}
	parts := strings.Split(strings.TrimPrefix(r, "SRC-"), "-")
	if len(parts) != 2 || len(parts[0]) != 4 || len(parts[1]) != 6 {
		t.Fatalf("receipt %q does not match the SRC-XXXX-XXXXXX shape", r)
	}
}

func TestNewReceiptDoesNotRepeat(t *testing.T) {
	seen := map[string]bool{}
	for i := 0; i < 1000; i++ {
		r := newReceipt()
		if seen[r] {
			t.Fatalf("receipt collision after %d draws: %s", i, r)
		}
		seen[r] = true
	}
}

func TestHashIsDeterministicAndDistinct(t *testing.T) {
	if hash("a") != hash("a") {
		t.Fatal("hash must be deterministic for the same input")
	}
	if hash("a") == hash("b") {
		t.Fatal("hash must differ for different inputs")
	}
}

func TestPct(t *testing.T) {
	cases := []struct {
		votes, eligible int64
		want            float64
	}{
		{0, 0, 0},
		{5, 0, 0},
		{50, 100, 50},
		{1, 3, 100.0 / 3.0},
	}
	for _, c := range cases {
		if got := pct(c.votes, c.eligible); got != c.want {
			t.Errorf("pct(%d,%d) = %v, want %v", c.votes, c.eligible, got, c.want)
		}
	}
}
