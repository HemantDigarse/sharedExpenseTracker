package com.spreetail.expenses.balance;

import com.spreetail.expenses.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for balance and settlement endpoints.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/groups/{groupId}/balances — full balance breakdown</li>
 * </ul>
 *
 * <p>The response includes:
 * <ul>
 *   <li>Per-user net balances (who owes / is owed)</li>
 *   <li>Contributing expense breakdown (Rohan's drilldown)</li>
 *   <li>Settlement suggestions (Aisha's simplified view)</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/groups/{groupId}/balances")
public class BalanceController {

    private final BalanceCalculationService balanceCalculationService;

    public BalanceController(BalanceCalculationService balanceCalculationService) {
        this.balanceCalculationService = balanceCalculationService;
    }

    /**
     * Calculates and returns all balances for a group.
     *
     * <p>This is the primary endpoint used by:
     * <ul>
     *   <li>Dashboard — quick balance summary per group</li>
     *   <li>BalanceSummary.jsx — Aisha's "one number per person" view</li>
     *   <li>BalanceBreakdown.jsx — Rohan's expense drilldown view</li>
     * </ul>
     */
    @GetMapping
    public ResponseEntity<ApiResponse<BalanceDTO>> getBalances(
            @PathVariable Long groupId) {
        BalanceDTO balances = balanceCalculationService.calculateBalances(groupId);
        return ResponseEntity.ok(
                ApiResponse.success(balances, "Balances calculated successfully")
        );
    }
}
