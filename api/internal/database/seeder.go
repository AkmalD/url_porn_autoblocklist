package database

import (
	"log"

	"github.com/lawanpmo/url-autoblocklist/internal/model"
)

// SeedBlockedDomains seeds initial blocked domains
func SeedBlockedDomains() error {
	log.Println("Seeding blocked domains...")

	// List of known adult domains to seed
	domains := []string{
		// Major adult sites
		"pornhub.com",
		"xvideos.com",
		"xnxx.com",
		"xhamster.com",
		"redtube.com",
		"youporn.com",
		"tube8.com",
		"spankbang.com",
		"eporner.com",
		"beeg.com",
		"porn.com",
		"pornone.com",
		"porntube.com",
		"porntrex.com",
		"porndig.com",
		"porn300.com",
		"pornhd.com",
		"pornhd3x.com",
		"fuq.com",
		"tnaflix.com",
		"drtuber.com",
		"sunporno.com",
		"upornia.com",
		"hclips.com",
		"txxx.com",
		"vjav.com",
		"jav.guru",
		"javhd.com",
		"javmost.com",
		"javfree.me",

		// Indonesian adult sites
		"bokep.com",
		"bokepindo.com",
		"bokephub.com",
		"bokepsex.com",
		"memek.com",
		"ngentot.com",
		"lendir.com",
		"streakmov.com",
		"indoxxi.bz",
		"lk21.tv",
		"layarkaca21.tv",

		// Adult content aggregators
		"theporndude.com",
		"ixxx.com",
		"pornmd.com",
		"nudevista.com",
		"tubegalore.com",
		"pornrabbit.com",
		"4tube.com",
		"fapdu.com",
		"fapvid.com",

		// Camming sites
		"chaturbate.com",
		"stripchat.com",
		"bongacams.com",
		"livejasmin.com",
		"cam4.com",
		"myfreecams.com",
		"camsoda.com",
		"flirt4free.com",

		// Hentai/Anime adult
		"hentaihaven.org",
		"hanime.tv",
		"nhentai.net",
		"hentai2read.com",
		"hentaistream.com",
		"hentaimama.io",
		"hentaigasm.com",
		"fakku.net",

		// Adult forums/communities
		"xnxx.tv",
		"pornbb.org",
		"forumophilia.com",
		"planetsuzy.org",
		"phun.org",
		"freeones.com",
		"indexxx.com",

		// Additional sites
		"motherless.com",
		"heavy-r.com",
		"efukt.com",
		"bestgore.com",
		"shockchan.com",
		"crazyshit.com",
	}

	for _, domain := range domains {
		// Check if domain already exists
		var existing model.BlockedDomain
		result := DB.Where("domain = ?", domain).First(&existing)

		if result.RowsAffected == 0 {
			// Create new domain entry
			newDomain := model.BlockedDomain{
				Domain:          domain,
				Category:        model.CategoryPornography,
				Source:          model.SourceSeeded,
				Status:          model.StatusActive,
				ConfidenceScore: 1.0,
			}

			if err := DB.Create(&newDomain).Error; err != nil {
				log.Printf("Error seeding domain %s: %v", domain, err)
				continue
			}
		}
	}

	// Count seeded domains
	var count int64
	DB.Model(&model.BlockedDomain{}).Where("source = ?", model.SourceSeeded).Count(&count)
	log.Printf("Seeded domains count: %d", count)

	return nil
}
