package handler

import (
	"strconv"

	"github.com/gofiber/fiber/v2"
	"github.com/google/uuid"
	"github.com/lawanpmo/url-autoblocklist/internal/model"
	"github.com/lawanpmo/url-autoblocklist/internal/service"
)

type BlocklistHandler struct {
	service service.BlocklistService
}

func NewBlocklistHandler(service service.BlocklistService) *BlocklistHandler {
	return &BlocklistHandler{service: service}
}

// ========================================
// PUBLIC ENDPOINTS
// ========================================

// GetSync returns all active blocked domains for device sync
// GET /api/blocklist/sync
func (h *BlocklistHandler) GetSync(c *fiber.Ctx) error {
	data, err := h.service.GetSyncData()
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "Failed to get sync data",
		})
	}
	return c.JSON(data)
}

// GetStats returns public statistics
// GET /api/blocklist/stats
func (h *BlocklistHandler) GetStats(c *fiber.Ctx) error {
	stats, err := h.service.GetStats()
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "Failed to get stats",
		})
	}

	// Return only public stats
	return c.JSON(fiber.Map{
		"total_domains":  stats.TotalDomains,
		"active_domains": stats.ActiveDomains,
		"total_blocks":   stats.TotalBlocks,
	})
}

// CheckDomain checks if a domain is blocked
// POST /api/blocklist/check
func (h *BlocklistHandler) CheckDomain(c *fiber.Ctx) error {
	var req struct {
		Domain string `json:"domain"`
	}

	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "Invalid request body",
		})
	}

	if req.Domain == "" {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "Domain is required",
		})
	}

	result, err := h.service.CheckDomain(req.Domain)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "Failed to check domain",
		})
	}

	return c.JSON(result)
}

// ReportDomain submits a domain report
// POST /api/blocklist/report
func (h *BlocklistHandler) ReportDomain(c *fiber.Ctx) error {
	var req struct {
		Domain  string  `json:"domain"`
		Reason  string  `json:"reason"`
		MLScore float64 `json:"ml_score"`
	}

	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "Invalid request body",
		})
	}

	if req.Domain == "" {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "Domain is required",
		})
	}

	ip := c.IP()
	if err := h.service.ReportDomain(req.Domain, req.Reason, req.MLScore, ip); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "Failed to submit report",
		})
	}

	return c.JSON(fiber.Map{
		"message": "Report submitted successfully",
	})
}

// ========================================
// ADMIN ENDPOINTS
// ========================================

// AdminGetStats returns full statistics
// GET /api/admin/blocklist/stats
func (h *BlocklistHandler) AdminGetStats(c *fiber.Ctx) error {
	stats, err := h.service.GetStats()
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "Failed to get stats",
		})
	}
	return c.JSON(stats)
}

// AdminGetDomains returns domain list with filters
// GET /api/admin/blocklist/domains
func (h *BlocklistHandler) AdminGetDomains(c *fiber.Ctx) error {
	status := c.Query("status")
	category := c.Query("category")
	limit, _ := strconv.Atoi(c.Query("limit", "50"))
	offset, _ := strconv.Atoi(c.Query("offset", "0"))

	data, err := h.service.GetDomains(status, category, limit, offset)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "Failed to get domains",
		})
	}
	return c.JSON(data)
}

// AdminAddDomain adds a single domain
// POST /api/admin/blocklist/domains
func (h *BlocklistHandler) AdminAddDomain(c *fiber.Ctx) error {
	var req struct {
		Domain   string `json:"domain"`
		Category string `json:"category"`
	}

	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "Invalid request body",
		})
	}

	if req.Domain == "" {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "Domain is required",
		})
	}

	category := model.DomainCategory(req.Category)
	if category == "" {
		category = model.CategoryPornography
	}

	if err := h.service.AddDomain(req.Domain, category, model.SourceAdminAdded); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "Failed to add domain",
		})
	}

	return c.Status(fiber.StatusCreated).JSON(fiber.Map{
		"message": "Domain added successfully",
	})
}

// AdminAddDomainsBulk adds multiple domains
// POST /api/admin/blocklist/domains/bulk
func (h *BlocklistHandler) AdminAddDomainsBulk(c *fiber.Ctx) error {
	var req struct {
		Domains  []string `json:"domains"`
		Category string   `json:"category"`
	}

	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "Invalid request body",
		})
	}

	if len(req.Domains) == 0 {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "Domains array is required",
		})
	}

	category := model.DomainCategory(req.Category)
	if category == "" {
		category = model.CategoryPornography
	}

	added, err := h.service.AddDomainsBulk(req.Domains, category)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "Failed to add domains",
		})
	}

	return c.Status(fiber.StatusCreated).JSON(fiber.Map{
		"message":     "Domains added successfully",
		"added_count": added,
	})
}

// AdminUpdateDomainStatus updates domain status
// PUT /api/admin/blocklist/domains/:id/status
func (h *BlocklistHandler) AdminUpdateDomainStatus(c *fiber.Ctx) error {
	idStr := c.Params("id")
	id, err := uuid.Parse(idStr)
	if err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "Invalid domain ID",
		})
	}

	var req struct {
		Status string `json:"status"`
	}

	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "Invalid request body",
		})
	}

	status := model.DomainStatus(req.Status)
	if status != model.StatusActive && status != model.StatusPendingReview && status != model.StatusRejected {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "Invalid status. Must be: active, pending_review, or rejected",
		})
	}

	if err := h.service.UpdateDomainStatus(id, status); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "Failed to update status",
		})
	}

	return c.JSON(fiber.Map{
		"message": "Status updated successfully",
	})
}

// AdminDeleteDomain deletes a domain
// DELETE /api/admin/blocklist/domains/:id
func (h *BlocklistHandler) AdminDeleteDomain(c *fiber.Ctx) error {
	idStr := c.Params("id")
	id, err := uuid.Parse(idStr)
	if err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "Invalid domain ID",
		})
	}

	if err := h.service.DeleteDomain(id); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "Failed to delete domain",
		})
	}

	return c.JSON(fiber.Map{
		"message": "Domain deleted successfully",
	})
}

// AdminGetReports returns pending reports
// GET /api/admin/blocklist/reports
func (h *BlocklistHandler) AdminGetReports(c *fiber.Ctx) error {
	limit, _ := strconv.Atoi(c.Query("limit", "50"))
	offset, _ := strconv.Atoi(c.Query("offset", "0"))

	data, err := h.service.GetPendingReports(limit, offset)
	if err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "Failed to get reports",
		})
	}
	return c.JSON(data)
}

// AdminReviewReport approves/rejects a report
// POST /api/admin/blocklist/reports/:id/review
func (h *BlocklistHandler) AdminReviewReport(c *fiber.Ctx) error {
	idStr := c.Params("id")
	id, err := uuid.Parse(idStr)
	if err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "Invalid report ID",
		})
	}

	var req struct {
		Approved bool   `json:"approved"`
		Notes    string `json:"notes"`
	}

	if err := c.BodyParser(&req); err != nil {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "Invalid request body",
		})
	}

	if err := h.service.ReviewReport(id, req.Approved, req.Notes); err != nil {
		return c.Status(fiber.StatusInternalServerError).JSON(fiber.Map{
			"error": "Failed to review report",
		})
	}

	return c.JSON(fiber.Map{
		"message": "Report reviewed successfully",
	})
}
