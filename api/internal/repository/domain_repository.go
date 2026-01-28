package repository

import (
	"github.com/google/uuid"
	"github.com/lawanpmo/url-autoblocklist/internal/model"
	"gorm.io/gorm"
)

type DomainRepository interface {
	GetAllActive() ([]model.BlockedDomain, error)
	GetByDomain(domain string) (*model.BlockedDomain, error)
	GetByID(id uuid.UUID) (*model.BlockedDomain, error)
	Create(domain *model.BlockedDomain) error
	CreateBulk(domains []model.BlockedDomain) error
	Update(domain *model.BlockedDomain) error
	Delete(id uuid.UUID) error
	IncrementBlockCount(domain string) error
	GetStats() (*DomainStats, error)
	GetAllWithFilters(status string, category string, limit int, offset int) ([]model.BlockedDomain, int64, error)
}

type DomainStats struct {
	TotalDomains    int64 `json:"total_domains"`
	ActiveDomains   int64 `json:"active_domains"`
	PendingDomains  int64 `json:"pending_domains"`
	TotalBlocks     int64 `json:"total_blocks"`
	TotalReports    int64 `json:"total_reports"`
	SeededDomains   int64 `json:"seeded_domains"`
	UserReported    int64 `json:"user_reported"`
	MLDetected      int64 `json:"ml_detected"`
}

type domainRepository struct {
	db *gorm.DB
}

func NewDomainRepository(db *gorm.DB) DomainRepository {
	return &domainRepository{db: db}
}

func (r *domainRepository) GetAllActive() ([]model.BlockedDomain, error) {
	var domains []model.BlockedDomain
	err := r.db.Where("status = ?", model.StatusActive).Find(&domains).Error
	return domains, err
}

func (r *domainRepository) GetByDomain(domain string) (*model.BlockedDomain, error) {
	var d model.BlockedDomain
	err := r.db.Where("domain = ?", domain).First(&d).Error
	if err != nil {
		return nil, err
	}
	return &d, nil
}

func (r *domainRepository) GetByID(id uuid.UUID) (*model.BlockedDomain, error) {
	var d model.BlockedDomain
	err := r.db.First(&d, id).Error
	if err != nil {
		return nil, err
	}
	return &d, nil
}

func (r *domainRepository) Create(domain *model.BlockedDomain) error {
	return r.db.Create(domain).Error
}

func (r *domainRepository) CreateBulk(domains []model.BlockedDomain) error {
	return r.db.CreateInBatches(domains, 100).Error
}

func (r *domainRepository) Update(domain *model.BlockedDomain) error {
	return r.db.Save(domain).Error
}

func (r *domainRepository) Delete(id uuid.UUID) error {
	return r.db.Delete(&model.BlockedDomain{}, id).Error
}

func (r *domainRepository) IncrementBlockCount(domain string) error {
	return r.db.Model(&model.BlockedDomain{}).
		Where("domain = ?", domain).
		UpdateColumn("times_blocked", gorm.Expr("times_blocked + 1")).Error
}

func (r *domainRepository) GetStats() (*DomainStats, error) {
	stats := &DomainStats{}

	r.db.Model(&model.BlockedDomain{}).Count(&stats.TotalDomains)
	r.db.Model(&model.BlockedDomain{}).Where("status = ?", model.StatusActive).Count(&stats.ActiveDomains)
	r.db.Model(&model.BlockedDomain{}).Where("status = ?", model.StatusPendingReview).Count(&stats.PendingDomains)
	r.db.Model(&model.BlockedDomain{}).Where("source = ?", model.SourceSeeded).Count(&stats.SeededDomains)
	r.db.Model(&model.BlockedDomain{}).Where("source = ?", model.SourceUserReport).Count(&stats.UserReported)
	r.db.Model(&model.BlockedDomain{}).Where("source = ?", model.SourceMLDetected).Count(&stats.MLDetected)

	// Sum of times_blocked
	r.db.Model(&model.BlockedDomain{}).Select("COALESCE(SUM(times_blocked), 0)").Scan(&stats.TotalBlocks)

	// Count reports
	r.db.Model(&model.DomainReport{}).Count(&stats.TotalReports)

	return stats, nil
}

func (r *domainRepository) GetAllWithFilters(status string, category string, limit int, offset int) ([]model.BlockedDomain, int64, error) {
	var domains []model.BlockedDomain
	var total int64

	query := r.db.Model(&model.BlockedDomain{})

	if status != "" {
		query = query.Where("status = ?", status)
	}
	if category != "" {
		query = query.Where("category = ?", category)
	}

	query.Count(&total)

	err := query.Order("created_at DESC").Limit(limit).Offset(offset).Find(&domains).Error
	return domains, total, err
}
