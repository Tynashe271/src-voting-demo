package database

import (
	"database/sql"
	"fmt"
	"log"
	"time"

	_ "github.com/lib/pq"
	"github.com/redis/go-redis/v9"
)

type Database struct {
	*sql.DB
}

type RedisClient struct {
	*redis.Client
}

func Initialize(cfg interface{}) (*Database, error) {
	// This will be implemented with the actual config structure
	// For now, creating a placeholder
	return &Database{}, nil
}

func InitializeRedis(cfg interface{}) (*RedisClient, error) {
	// This will be implemented with the actual config structure
	// For now, creating a placeholder
	return &RedisClient{}, nil
}

func (db *Database) Close() error {
	if db.DB != nil {
		return db.DB.Close()
	}
	return nil
}

func (r *RedisClient) Close() error {
	if r.Client != nil {
		return r.Client.Close()
	}
	return nil
}

// Test database connection
func TestConnection(host, port, user, password, dbname string) error {
	connStr := fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=disable",
		host, port, user, password, dbname)

	db, err := sql.Open("postgres", connStr)
	if err != nil {
		return fmt.Errorf("failed to open database: %w", err)
	}
	defer db.Close()

	err = db.Ping()
	if err != nil {
		return fmt.Errorf("failed to ping database: %w", err)
	}

	log.Println("Database connection successful")
	return nil
}

// Get database connection string
func GetConnectionString(host, port, user, password, dbname, sslmode string) string {
	return fmt.Sprintf("host=%s port=%s user=%s password=%s dbname=%s sslmode=%s",
		host, port, user, password, dbname, sslmode)
}

// Create connection with retry logic
func CreateConnectionWithRetry(connStr string, maxRetries int, retryInterval time.Duration) (*sql.DB, error) {
	var db *sql.DB
	var err error

	for i := 0; i < maxRetries; i++ {
		db, err = sql.Open("postgres", connStr)
		if err != nil {
			log.Printf("Attempt %d: failed to open database: %v", i+1, err)
			time.Sleep(retryInterval)
			continue
		}

		err = db.Ping()
		if err != nil {
			log.Printf("Attempt %d: failed to ping database: %v", i+1, err)
			db.Close()
			time.Sleep(retryInterval)
			continue
		}

		// Set connection pool settings
		db.SetMaxOpenConns(25)
		db.SetMaxIdleConns(5)
		db.SetConnMaxLifetime(5 * time.Minute)

		log.Printf("Database connection established on attempt %d", i+1)
		return db, nil
	}

	return nil, fmt.Errorf("failed to establish database connection after %d attempts: %w", maxRetries, err)
}