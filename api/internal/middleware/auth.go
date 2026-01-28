package middleware

import (
	"os"
	"strings"

	"github.com/gofiber/fiber/v2"
)

// AdminAuth middleware for admin endpoints
// Uses a simple API key for authentication
func AdminAuth() fiber.Handler {
	return func(c *fiber.Ctx) error {
		// Get API key from environment
		adminKey := os.Getenv("ADMIN_API_KEY")
		if adminKey == "" {
			adminKey = "admin-secret-key-change-in-production"
		}

		// Get authorization header
		authHeader := c.Get("Authorization")
		if authHeader == "" {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{
				"error": "Authorization header required",
			})
		}

		// Check Bearer token
		if !strings.HasPrefix(authHeader, "Bearer ") {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{
				"error": "Invalid authorization format. Use: Bearer <api_key>",
			})
		}

		token := strings.TrimPrefix(authHeader, "Bearer ")
		if token != adminKey {
			return c.Status(fiber.StatusUnauthorized).JSON(fiber.Map{
				"error": "Invalid API key",
			})
		}

		return c.Next()
	}
}
