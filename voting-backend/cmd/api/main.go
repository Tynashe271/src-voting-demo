package main

import (
	"log"
	"voting-backend/internal/api"
	"voting-backend/internal/config"
	"voting-backend/internal/database"
)

func main() {
	// Load configuration
	cfg, err := config.Load()
	if err != nil {
		log.Fatalf("Failed to load configuration: %v", err)
	}

	// Initialize database
	db, err := database.Initialize(cfg.Database)
	if err != nil {
		log.Fatalf("Failed to initialize database: %v", err)
	}
	defer db.Close()

	// Initialize Redis
	redis, err := database.InitializeRedis(cfg.Redis)
	if err != nil {
		log.Fatalf("Failed to initialize Redis: %v", err)
	}
	defer redis.Close()

	// Setup API server
	server := api.NewServer(cfg, db, redis)

	// Start server
	log.Printf("Starting voting backend server on %s", cfg.Server.Address)
	if err := server.Run(); err != nil {
		log.Fatalf("Server failed: %v", err)
	}
}