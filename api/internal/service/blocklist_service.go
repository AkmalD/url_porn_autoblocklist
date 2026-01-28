package service

import (
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/lawanpmo/url-autoblocklist/internal/model"
	"github.com/lawanpmo/url-autoblocklist/internal/repository"
)

type BlocklistService interface {
	// Sync endpoints
	GetSyncData() (*SyncResponse, error)
	CheckDomain(domain string) (*CheckResponse, error)

	// Report endpoints
	ReportDomain(domain string, reason string, mlScore float64, ip string) error

	// Admin endpoints
	GetStats() (*repository.DomainStats, error)
	GetDomains(status string, category string, limit int, offset int) (*DomainListResponse, error)
	AddDomain(domain string, category model.DomainCategory, source model.DomainSource) error
	AddDomainsBulk(domains []string, category model.DomainCategory) (int, error)
	UpdateDomainStatus(id uuid.UUID, status model.DomainStatus) error
	DeleteDomain(id uuid.UUID) error

	// Report management
	GetPendingReports(limit int, offset int) (*ReportListResponse, error)
	ReviewReport(id uuid.UUID, approved bool, notes string) error
}

type SyncResponse struct {
	Domains     []string  `json:"domains"`
	Version     int64     `json:"version"`
	LastUpdated time.Time `json:"last_updated"`
	Count       int       `json:"count"`
}

type CheckResponse struct {
	Domain    string `json:"domain"`
	IsBlocked bool   `json:"is_blocked"`
	Category  string `json:"category,omitempty"`
}

type DomainListResponse struct {
	Domains []model.BlockedDomain `json:"domains"`
	Total   int64                 `json:"total"`
	Limit   int                   `json:"limit"`
	Offset  int                   `json:"offset"`
}

type ReportListResponse struct {
	Reports []model.DomainReport `json:"reports"`
	Total   int64                `json:"total"`
	Limit   int                  `json:"limit"`
	Offset  int                  `json:"offset"`
}

type blocklistService struct {
	domainRepo repository.DomainRepository
	reportRepo repository.ReportRepository
}

func NewBlocklistService(domainRepo repository.DomainRepository, reportRepo repository.ReportRepository) BlocklistService {
	return &blocklistService{
		domainRepo: domainRepo,
		reportRepo: reportRepo,
	}
}

func (s *blocklistService) GetSyncData() (*SyncResponse, error) {
	domains, err := s.domainRepo.GetAllActive()
	if err != nil {
		return nil, err
	}

	domainList := make([]string, len(domains))
	for i, d := range domains {
		domainList[i] = d.Domain
	}

	return &SyncResponse{
		Domains:     domainList,
		Version:     time.Now().Unix(),
		LastUpdated: time.Now(),
		Count:       len(domainList),
	}, nil
}

func (s *blocklistService) CheckDomain(domain string) (*CheckResponse, error) {
	// Normalize domain
	domain = normalizeDomain(domain)

	d, err := s.domainRepo.GetByDomain(domain)
	if err != nil {
		// Not found = not blocked
		return &CheckResponse{
			Domain:    domain,
			IsBlocked: false,
		}, nil
	}

	// Increment block count
	s.domainRepo.IncrementBlockCount(domain)

	return &CheckResponse{
		Domain:    domain,
		IsBlocked: d.Status == model.StatusActive,
		Category:  string(d.Category),
	}, nil
}

func (s *blocklistService) ReportDomain(domain string, reason string, mlScore float64, ip string) error {
	domain = normalizeDomain(domain)

	report := &model.DomainReport{
		Domain:     domain,
		Reason:     reason,
		MLScore:    mlScore,
		ReporterIP: ip,
		Status:     "pending",
	}

	return s.reportRepo.Create(report)
}

func (s *blocklistService) GetStats() (*repository.DomainStats, error) {
	return s.domainRepo.GetStats()
}

func (s *blocklistService) GetDomains(status string, category string, limit int, offset int) (*DomainListResponse, error) {
	if limit <= 0 {
		limit = 50
	}
	if limit > 500 {
		limit = 500
	}

	domains, total, err := s.domainRepo.GetAllWithFilters(status, category, limit, offset)
	if err != nil {
		return nil, err
	}

	return &DomainListResponse{
		Domains: domains,
		Total:   total,
		Limit:   limit,
		Offset:  offset,
	}, nil
}

func (s *blocklistService) AddDomain(domain string, category model.DomainCategory, source model.DomainSource) error {
	domain = normalizeDomain(domain)

	// Check if already exists
	existing, _ := s.domainRepo.GetByDomain(domain)
	if existing != nil {
		return nil // Already exists, skip
	}

	newDomain := &model.BlockedDomain{
		Domain:          domain,
		Category:        category,
		Source:          source,
		Status:          model.StatusActive,
		ConfidenceScore: 1.0,
	}

	return s.domainRepo.Create(newDomain)
}

func (s *blocklistService) AddDomainsBulk(domains []string, category model.DomainCategory) (int, error) {
	var newDomains []model.BlockedDomain
	added := 0

	for _, domain := range domains {
		domain = normalizeDomain(domain)
		if domain == "" {
			continue
		}

		// Check if exists
		existing, _ := s.domainRepo.GetByDomain(domain)
		if existing != nil {
			continue
		}

		newDomains = append(newDomains, model.BlockedDomain{
			Domain:          domain,
			Category:        category,
			Source:          model.SourceAdminAdded,
			Status:          model.StatusActive,
			ConfidenceScore: 1.0,
		})
		added++
	}

	if len(newDomains) > 0 {
		if err := s.domainRepo.CreateBulk(newDomains); err != nil {
			return 0, err
		}
	}

	return added, nil
}

func (s *blocklistService) UpdateDomainStatus(id uuid.UUID, status model.DomainStatus) error {
	domain, err := s.domainRepo.GetByID(id)
	if err != nil {
		return err
	}

	domain.Status = status
	return s.domainRepo.Update(domain)
}

func (s *blocklistService) DeleteDomain(id uuid.UUID) error {
	return s.domainRepo.Delete(id)
}

func (s *blocklistService) GetPendingReports(limit int, offset int) (*ReportListResponse, error) {
	if limit <= 0 {
		limit = 50
	}

	reports, total, err := s.reportRepo.GetPending(limit, offset)
	if err != nil {
		return nil, err
	}

	return &ReportListResponse{
		Reports: reports,
		Total:   total,
		Limit:   limit,
		Offset:  offset,
	}, nil
}

func (s *blocklistService) ReviewReport(id uuid.UUID, approved bool, notes string) error {
	report, err := s.reportRepo.GetByID(id)
	if err != nil {
		return err
	}

	status := "rejected"
	if approved {
		status = "approved"

		// Add domain to blocklist
		s.AddDomain(report.Domain, model.CategoryPornography, model.SourceUserReport)
	}

	return s.reportRepo.UpdateStatus(id, status, notes)
}

// normalizeDomain cleans up domain input
func normalizeDomain(domain string) string {
	domain = strings.ToLower(strings.TrimSpace(domain))
	domain = strings.TrimPrefix(domain, "https://")
	domain = strings.TrimPrefix(domain, "http://")
	domain = strings.TrimPrefix(domain, "www.")

	// Remove path
	if idx := strings.Index(domain, "/"); idx != -1 {
		domain = domain[:idx]
	}

	// Remove port
	if idx := strings.Index(domain, ":"); idx != -1 {
		domain = domain[:idx]
	}

	return domain
}
