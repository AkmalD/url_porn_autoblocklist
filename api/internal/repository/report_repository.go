package repository

import (
	"time"

	"github.com/google/uuid"
	"github.com/lawanpmo/url-autoblocklist/internal/model"
	"gorm.io/gorm"
)

type ReportRepository interface {
	Create(report *model.DomainReport) error
	GetByID(id uuid.UUID) (*model.DomainReport, error)
	GetPending(limit int, offset int) ([]model.DomainReport, int64, error)
	UpdateStatus(id uuid.UUID, status string, notes string) error
}

type reportRepository struct {
	db *gorm.DB
}

func NewReportRepository(db *gorm.DB) ReportRepository {
	return &reportRepository{db: db}
}

func (r *reportRepository) Create(report *model.DomainReport) error {
	return r.db.Create(report).Error
}

func (r *reportRepository) GetByID(id uuid.UUID) (*model.DomainReport, error) {
	var report model.DomainReport
	err := r.db.First(&report, id).Error
	if err != nil {
		return nil, err
	}
	return &report, nil
}

func (r *reportRepository) GetPending(limit int, offset int) ([]model.DomainReport, int64, error) {
	var reports []model.DomainReport
	var total int64

	query := r.db.Model(&model.DomainReport{}).Where("status = ?", "pending")
	query.Count(&total)

	err := query.Order("created_at DESC").Limit(limit).Offset(offset).Find(&reports).Error
	return reports, total, err
}

func (r *reportRepository) UpdateStatus(id uuid.UUID, status string, notes string) error {
	now := time.Now()
	return r.db.Model(&model.DomainReport{}).
		Where("id = ?", id).
		Updates(map[string]interface{}{
			"status":       status,
			"review_notes": notes,
			"reviewed_at":  &now,
		}).Error
}
