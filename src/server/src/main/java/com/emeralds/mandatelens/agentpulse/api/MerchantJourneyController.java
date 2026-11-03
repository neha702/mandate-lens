package com.emeralds.mandatelens.agentpulse.api;

import com.emeralds.mandatelens.agentpulse.journey.JourneyQueryService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Merchant-facing read-only endpoint for the recorded history of one journey. */
@Validated
@RestController
@RequestMapping("/api/v1/journeys")
public class MerchantJourneyController {
    private final JourneyQueryService journeyQueryService;

    public MerchantJourneyController(JourneyQueryService journeyQueryService) {
        this.journeyQueryService = journeyQueryService;
    }

    @GetMapping
    public List<com.emeralds.mandatelens.agentpulse.journey.JourneyReadRepository.JourneyListItem> listJourneys(
            @RequestParam @NotBlank String merchantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {
        return journeyQueryService.listJourneys(merchantId, status, search);
    }

    @GetMapping("/{journeyId}")
    public JourneyQueryService.JourneyDetail getJourney(
            @PathVariable @NotBlank String journeyId,
            @RequestParam @NotBlank String merchantId) {
        return journeyQueryService.getJourney(journeyId, merchantId);
    }
}
