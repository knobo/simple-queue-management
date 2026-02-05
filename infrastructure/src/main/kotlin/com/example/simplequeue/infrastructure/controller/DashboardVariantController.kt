package com.example.simplequeue.infrastructure.controller

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/**
 * Dashboard Variant Controller.
 * Serves role-specific dashboard variants for different user types.
 * 
 * - variant1: Queue Owner dashboard (OWNER or SELLER)
 * - variant2: Queue Operator dashboard (OPERATOR or OWNER)
 * - variant3: Seller dashboard (SELLER or SUPERADMIN)
 * - variant4: Superadmin dashboard (SUPERADMIN only)
 */
@Controller
class DashboardVariantController {

    companion object {
        val logger: Logger = LoggerFactory.getLogger(DashboardVariantController::class.java)
    }

    /**
     * Dashboard Variant 1 - Queue Owner Dashboard
     * Accessible by: OWNER or SELLER
     */
    @GetMapping("/dashboard-variant1")
    @PreAuthorize("hasAnyRole('OWNER', 'SELLER')")
    fun dashboardVariant1(): String {
        logger.info("Serving dashboard-variant1 for queue owner")
        return "dashboard-variants/dashboard-variant1"
    }

    /**
     * Dashboard Variant 2 - Queue Operator Dashboard
     * Accessible by: OPERATOR or OWNER
     */
    @GetMapping("/dashboard-variant2")
    @PreAuthorize("hasAnyRole('OPERATOR', 'OWNER')")
    fun dashboardVariant2(): String {
        logger.info("Serving dashboard-variant2 for queue operator")
        return "dashboard-variants/dashboard-variant2"
    }

    /**
     * Dashboard Variant 3 - Seller Dashboard
     * Accessible by: SELLER or SUPERADMIN
     */
    @GetMapping("/dashboard-variant3")
    @PreAuthorize("hasAnyRole('SELLER', 'SUPERADMIN')")
    fun dashboardVariant3(): String {
        logger.info("Serving dashboard-variant3 for seller")
        return "dashboard-variants/dashboard-variant3"
    }

    /**
     * Dashboard Variant 4 - Superadmin Dashboard
     * Accessible by: SUPERADMIN only
     */
    @GetMapping("/dashboard-variant4")
    @PreAuthorize("hasRole('SUPERADMIN')")
    fun dashboardVariant4(): String {
        logger.info("Serving dashboard-variant4 for superadmin")
        return "dashboard-variants/dashboard-variant4"
    }
}
