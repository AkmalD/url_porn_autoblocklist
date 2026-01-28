package main

import (
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/cors"
	"github.com/gofiber/fiber/v2/middleware/logger"
	"github.com/gofiber/fiber/v2/middleware/recover"
	"github.com/lawanpmo/url-autoblocklist/internal/config"
	"github.com/lawanpmo/url-autoblocklist/internal/database"
	"github.com/lawanpmo/url-autoblocklist/internal/handler"
	"github.com/lawanpmo/url-autoblocklist/internal/middleware"
	"github.com/lawanpmo/url-autoblocklist/internal/repository"
	"github.com/lawanpmo/url-autoblocklist/internal/service"
)

func main() {
	// Load configuration
	cfg := config.Load()

	// Connect to database
	if err := database.Connect(cfg.DatabaseURL); err != nil {
		log.Fatalf("Failed to connect to database: %v", err)
	}
	defer database.Close()

	// Run migrations
	if err := database.Migrate(); err != nil {
		log.Fatalf("Failed to run migrations: %v", err)
	}

	// Seed initial data
	if err := database.SeedBlockedDomains(); err != nil {
		log.Printf("Warning: Failed to seed domains: %v", err)
	}

	// Initialize repositories
	domainRepo := repository.NewDomainRepository(database.DB)
	reportRepo := repository.NewReportRepository(database.DB)

	// Initialize services
	blocklistService := service.NewBlocklistService(domainRepo, reportRepo)

	// Initialize handlers
	blocklistHandler := handler.NewBlocklistHandler(blocklistService)

	// Create Fiber app
	app := fiber.New(fiber.Config{
		AppName: "URL AutoBlocklist API",
	})

	// Middleware
	app.Use(recover.New())
	app.Use(logger.New())
	app.Use(cors.New(cors.Config{
		AllowOrigins: "*",
		AllowMethods: "GET,POST,PUT,DELETE,OPTIONS",
		AllowHeaders: "Origin,Content-Type,Accept,Authorization",
	}))

	// Health check
	app.Get("/health", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{
			"status":  "healthy",
			"service": "url-autoblocklist",
		})
	})

	// API Routes
	api := app.Group("/api")

	// Public blocklist endpoints
	blocklist := api.Group("/blocklist")
	blocklist.Get("/sync", blocklistHandler.GetSync)
	blocklist.Get("/stats", blocklistHandler.GetStats)
	blocklist.Post("/check", blocklistHandler.CheckDomain)
	blocklist.Post("/report", blocklistHandler.ReportDomain)

	// Admin endpoints (protected)
	admin := api.Group("/admin", middleware.AdminAuth())
	adminBlocklist := admin.Group("/blocklist")
	adminBlocklist.Get("/stats", blocklistHandler.AdminGetStats)
	adminBlocklist.Get("/domains", blocklistHandler.AdminGetDomains)
	adminBlocklist.Post("/domains", blocklistHandler.AdminAddDomain)
	adminBlocklist.Post("/domains/bulk", blocklistHandler.AdminAddDomainsBulk)
	adminBlocklist.Put("/domains/:id/status", blocklistHandler.AdminUpdateDomainStatus)
	adminBlocklist.Delete("/domains/:id", blocklistHandler.AdminDeleteDomain)
	adminBlocklist.Get("/reports", blocklistHandler.AdminGetReports)
	adminBlocklist.Post("/reports/:id/review", blocklistHandler.AdminReviewReport)

	// Graceful shutdown
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)

	go func() {
		<-quit
		log.Println("Shutting down server...")
		if err := app.Shutdown(); err != nil {
			log.Printf("Error shutting down: %v", err)
		}
	}()

	// Start server
	log.Printf("Server starting on port %s", cfg.Port)
	if err := app.Listen(":" + cfg.Port); err != nil {
		log.Fatalf("Failed to start server: %v", err)
	}
}
