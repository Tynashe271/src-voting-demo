package main

import (
	"database/sql"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"

	_ "github.com/jackc/pgx/v5/stdlib"
)

func main() {
	dsn := os.Getenv("DATABASE_URL")
	if dsn == "" {
		panic("DATABASE_URL is required")
	}
	root := os.Getenv("MIGRATIONS_DIR")
	if root == "" {
		root = "migrations"
	}
	files, err := filepath.Glob(filepath.Join(root, "*.sql"))
	if err != nil {
		panic(err)
	}
	sort.Strings(files)
	db, err := sql.Open("pgx", dsn)
	if err != nil {
		panic(err)
	}
	defer db.Close()
	if _, err = db.Exec(`CREATE TABLE IF NOT EXISTS schema_migrations (version text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())`); err != nil {
		panic(err)
	}
	for _, file := range files {
		version := filepath.Base(file)
		var applied bool
		if err = db.QueryRow(`SELECT EXISTS(SELECT 1 FROM schema_migrations WHERE version=$1)`, version).Scan(&applied); err != nil {
			panic(err)
		}
		if applied {
			fmt.Println("skip", version)
			continue
		}
		contents, readErr := os.ReadFile(file)
		if readErr != nil {
			panic(readErr)
		}
		tx, txErr := db.Begin()
		if txErr != nil {
			panic(txErr)
		}
		if _, txErr = tx.Exec(string(contents)); txErr == nil {
			_, txErr = tx.Exec(`INSERT INTO schema_migrations(version) VALUES ($1)`, version)
		}
		if txErr != nil {
			_ = tx.Rollback()
			panic(fmt.Errorf("apply %s: %w", version, txErr))
		}
		if txErr = tx.Commit(); txErr != nil {
			panic(txErr)
		}
		fmt.Println("applied", version)
	}
	if strings.TrimSpace(os.Getenv("UNIVERSITY_TIMEZONE")) == "" {
		fmt.Println("UNIVERSITY_TIMEZONE defaults to Africa/Harare in the API; database trigger uses the same default")
	}
}
