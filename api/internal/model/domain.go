package model

import (
	"time"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

// DomainCategory represents the category of blocked domain
type DomainCategory string

const (
	CategoryPornography DomainCategory = "pornography"
	CategoryGambling    DomainCategory = "gambling"
	CategoryMalware     DomainCategory = "malware"
	CategoryPhishing    DomainCategory = "phishing"
	CategoryOther       DomainCategory = "other"
)

// DomainSource represents how the domain was added
type DomainSource string

const (
	SourceSeeded     DomainSource = "seeded"
	SourceUserReport DomainSource = "user_report"
	SourceMLDetected DomainSource = "ml_detected"
	SourceAdminAdded DomainSource = "admin_added"
)

// DomainStatus represents the review status
type DomainStatus string

const (
	StatusActive        DomainStatus = "active"
	StatusPendingReview DomainStatus = "pending_review"
	StatusRejected      DomainStatus = "rejected"
)

// BlockedDomain represents a domain in the blocklist
type BlockedDomain struct {
	ID              uuid.UUID      `gorm:"type:uuid;primary_key;default:gen_random_uuid()" json:"id"`
	Domain          string         `gorm:"uniqueIndex;not null" json:"domain"`
	Category        DomainCategory `gorm:"type:varchar(50);default:'pornography'" json:"category"`
	Source          DomainSource   `gorm:"type:varchar(50);default:'seeded'" json:"source"`
	Status          DomainStatus   `gorm:"type:varchar(50);default:'active'" json:"status"`
	ConfidenceScore float64        `gorm:"default:1.0" json:"confidence_score"`
	TimesBlocked    int64          `gorm:"default:0" json:"times_blocked"`
	TimesReported   int64          `gorm:"default:0" json:"times_reported"`
	CreatedAt       time.Time      `json:"created_at"`
	UpdatedAt       time.Time      `json:"updated_at"`
}

// DomainReport represents a user-submitted report
type DomainReport struct {
	ID           uuid.UUID `gorm:"type:uuid;primary_key;default:gen_random_uuid()" json:"id"`
	Domain       string    `gorm:"not null" json:"domain"`
	ReporterIP   string    `gorm:"type:varchar(45)" json:"reporter_ip"`
	Reason       string    `gorm:"type:text" json:"reason"`
	MLScore      float64   `json:"ml_score"`
	Status       string    `gorm:"type:varchar(50);default:'pending'" json:"status"` // pending, approved, rejected
	ReviewedAt   *time.Time `json:"reviewed_at"`
	ReviewNotes  string    `gorm:"type:text" json:"review_notes"`
	CreatedAt    time.Time `json:"created_at"`
	UpdatedAt    time.Time `json:"updated_at"`
}

// BlockStats represents blocking statistics
type BlockStats struct {
	ID           uuid.UUID `gorm:"type:uuid;primary_key;default:gen_random_uuid()" json:"id"`
	Domain       string    `gorm:"index;not null" json:"domain"`
	DeviceID     string    `gorm:"type:varchar(100)" json:"device_id"`
	BlockedAt    time.Time `gorm:"index" json:"blocked_at"`
	DetectionMethod string `gorm:"type:varchar(50)" json:"detection_method"` // blocklist, ml
	MLScore      float64   `json:"ml_score"`
}

// BeforeCreate hook to generate UUID
func (d *BlockedDomain) BeforeCreate(tx *gorm.DB) error {
	if d.ID == uuid.Nil {
		d.ID = uuid.New()
	}
	return nil
}

func (r *DomainReport) BeforeCreate(tx *gorm.DB) error {
	if r.ID == uuid.Nil {
		r.ID = uuid.New()
	}
	return nil
}

func (s *BlockStats) BeforeCreate(tx *gorm.DB) error {
	if s.ID == uuid.Nil {
		s.ID = uuid.New()
	}
	return nil
}
