package org.couponmanagement.service;

import org.couponmanagement.cache.BudgetCacheService;
import org.couponmanagement.dto.BudgetCheckResult;
import org.couponmanagement.dto.BudgetErrorCode;
import org.couponmanagement.entity.Budget;
import org.couponmanagement.entity.CouponBudgetUsage;
import org.couponmanagement.entity.RegisterStatus;
import org.couponmanagement.repository.BudgetRepository;
import org.couponmanagement.repository.CouponBudgetUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true",
    "logging.level.org.springframework.transaction=DEBUG"
})
class BudgetServiceTransactionIntegrationTest {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private CouponBudgetUsageRepository couponBudgetUsageRepository;

    @Mock
    private BudgetCacheService budgetCacheService;

    @Mock
    private BudgetEventProducer budgetEventProducer;

    private Integer testBudgetId;
    private static final BigDecimal INITIAL_BUDGET_AMOUNT = new BigDecimal("100.00");

    @BeforeEach
    void setUp() {
        // Clear any existing data
        couponBudgetUsageRepository.deleteAll();
        budgetRepository.deleteAll();

        // Create a test budget
        Budget testBudget = Budget.builder()
                .remaining(INITIAL_BUDGET_AMOUNT)
                .build();

        Budget savedBudget = budgetRepository.save(testBudget);
        testBudgetId = savedBudget.getId();
    }

    @Test
    void proceedBudgetCheckDB_sufficientBudget_successfulTransactionCommit() {
        // Arrange
        String transactionId = "tx-success-integration";
        Integer couponId = 100;
        Integer userId = 1001;
        BigDecimal amount = new BigDecimal("20.00");


        // Get initial state
        Budget initialBudget = budgetRepository.findById(testBudgetId).orElseThrow();
        int initialUsageCount = couponBudgetUsageRepository.countByBudgetId(testBudgetId);

        // Act
        BudgetCheckResult result = budgetService.proceedBudgetCheckDB(transactionId, testBudgetId, couponId, userId, amount);

        // Assert - Transaction should succeed
        assertTrue(result.success(), "Transaction should succeed with sufficient budget");
        assertEquals(BudgetErrorCode.NONE, result.errorCode());

        // Verify database state changes are committed
        Budget updatedBudget = budgetRepository.findById(testBudgetId).orElseThrow();
        assertEquals(INITIAL_BUDGET_AMOUNT.subtract(amount), updatedBudget.getRemaining(), "Remaining amount should be reduced");

        // Verify usage record was created
        int finalUsageCount = couponBudgetUsageRepository.countByBudgetId(testBudgetId);
        assertEquals(initialUsageCount + 1, finalUsageCount, "Usage record should be created");

        // Verify the specific usage record exists
        CouponBudgetUsage usageRecord = couponBudgetUsageRepository.findByTransactionId(transactionId);
        assertNotNull(usageRecord, "Usage record should exist");
        assertEquals(testBudgetId, usageRecord.getBudgetId());
        assertEquals(couponId, usageRecord.getCouponId());
        assertEquals(userId, usageRecord.getUserId());
        assertEquals(amount, usageRecord.getAmount());
        assertEquals(RegisterStatus.REGISTERED, usageRecord.getStatus());
    }

    @Test
    void proceedBudgetCheckDB_insufficientBudget_transactionRollback() {
        // Arrange - Try to deduct more than available
        String transactionId = "tx-insufficient-integration";
        Integer couponId = 200;
        Integer userId = 2001;
        BigDecimal amount = new BigDecimal("150.00");

        // Get initial state
        Budget initialBudget = budgetRepository.findById(testBudgetId).orElseThrow();
        int initialUsageCount = couponBudgetUsageRepository.countByBudgetId(testBudgetId);
        BigDecimal initialRemainingAmount = initialBudget.getRemaining();

        // Act
        BudgetCheckResult result = budgetService.proceedBudgetCheckDB(transactionId, testBudgetId, couponId, userId, amount);

        // Assert - Transaction should fail
        assertFalse(result.success(), "Transaction should fail with insufficient budget");
        assertEquals(BudgetErrorCode.INSUFFICIENT_BUDGET, result.errorCode());

        // Verify database state is rolled back (no changes)
        Budget unchangedBudget = budgetRepository.findById(testBudgetId).orElseThrow();
        assertEquals(initialRemainingAmount, unchangedBudget.getRemaining(), "Remaining amount should remain unchanged due to rollback");

        // Verify no usage record was created (rollback occurred)
        int finalUsageCount = couponBudgetUsageRepository.countByBudgetId(testBudgetId);
        assertEquals(initialUsageCount, finalUsageCount, "No usage record should be created due to rollback");

        // Verify the specific usage record does NOT exist
        CouponBudgetUsage usageRecord = couponBudgetUsageRepository.findByTransactionId(transactionId);
        assertNull(usageRecord, "Usage record should not exist due to transaction rollback");
    }

    @Test
    void proceedBudgetCheckDB_duplicateTransaction_noBudgetDeduction() {
        // Arrange - First insert a usage record manually to simulate duplicate
        String transactionId = "tx-duplicate-integration";
        Integer couponId = 300;
        Integer userId = 3001;
        BigDecimal amount = new BigDecimal("30.00");

        // Create initial usage record
        CouponBudgetUsage existingUsage = CouponBudgetUsage.builder()
                .id(transactionId)
                .budgetId(testBudgetId)
                .couponId(couponId)
                .userId(userId)
                .amount(amount)
                .status(RegisterStatus.REGISTERED)
                .usage_time(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        couponBudgetUsageRepository.save(existingUsage);

        // Get initial budget state
        Budget initialBudget = budgetRepository.findById(testBudgetId).orElseThrow();
        BigDecimal initialRemainingAmount = initialBudget.getRemaining();
        int initialUsageCount = couponBudgetUsageRepository.countByBudgetId(testBudgetId);

        // Act - Try to insert duplicate
        BudgetCheckResult result = budgetService.proceedBudgetCheckDB(transactionId, testBudgetId, couponId, userId, amount);

        // Assert
        assertFalse(result.success(), "Duplicate transaction should fail");
        assertEquals(BudgetErrorCode.ALREADY_RESERVED, result.errorCode());

        // Verify budget remains unchanged (no double deduction)
        Budget unchangedBudget = budgetRepository.findById(testBudgetId).orElseThrow();
        assertEquals(initialRemainingAmount, unchangedBudget.getRemaining(), "Remaining amount should not change on duplicate");

        // Verify usage count remains the same
        int finalUsageCount = couponBudgetUsageRepository.countByBudgetId(testBudgetId);
        assertEquals(initialUsageCount, finalUsageCount, "Usage count should remain same on duplicate");
    }

    @Test
    void proceedBudgetCheckDB_multipleSuccessfulTransactions_correctBudgetTracking() {
        // Arrange - Multiple transactions
        String transactionId1 = "tx-multi-1";
        String transactionId2 = "tx-multi-2";
        Integer couponId1 = 401;
        Integer couponId2 = 402;
        Integer userId = 4001;
        BigDecimal amount1 = new BigDecimal("25.00");
        BigDecimal amount2 = new BigDecimal("35.00");

        // Act - Execute multiple transactions
        BudgetCheckResult result1 = budgetService.proceedBudgetCheckDB(transactionId1, testBudgetId, couponId1, userId, amount1);
        BudgetCheckResult result2 = budgetService.proceedBudgetCheckDB(transactionId2, testBudgetId, couponId2, userId, amount2);

        assertTrue(result1.success(), "First transaction should succeed");
        assertTrue(result2.success(), "Second transaction should succeed");
        assertEquals(BudgetErrorCode.NONE, result1.errorCode());
        assertEquals(BudgetErrorCode.NONE, result2.errorCode());

        // Verify final budget state
        Budget finalBudget = budgetRepository.findById(testBudgetId).orElseThrow();
        BigDecimal expectedUsedAmount = amount1.add(amount2);
        BigDecimal expectedRemainingAmount = INITIAL_BUDGET_AMOUNT.subtract(expectedUsedAmount);

        assertEquals(expectedRemainingAmount, finalBudget.getRemaining(), "Remaining amount should be correctly calculated");

        // Verify both usage records exist
        assertNotNull(couponBudgetUsageRepository.findByTransactionId(transactionId1), "First usage record should exist");
        assertNotNull(couponBudgetUsageRepository.findByTransactionId(transactionId2), "Second usage record should exist");
        assertEquals(2, couponBudgetUsageRepository.countByBudgetId(testBudgetId), "Should have two usage records");
    }

    @Test
    void proceedBudgetCheckDB_partialFailure_onlySuccessfulTransactionsCommitted() {
        // Arrange - One successful, one that will fail
        String successTransactionId = "tx-success";
        String failTransactionId = "tx-fail";
        Integer couponId1 = 501;
        Integer couponId2 = 502;
        Integer userId = 5001;
        BigDecimal successAmount = new BigDecimal("30.00");
        BigDecimal failAmount = new BigDecimal("80.00"); // This will cause insufficient budget

        // Act - Execute successful transaction first
        BudgetCheckResult successResult = budgetService.proceedBudgetCheckDB(
                successTransactionId, testBudgetId, couponId1, userId, successAmount);

        // Now try failing transaction
        BudgetCheckResult failResult = budgetService.proceedBudgetCheckDB(
                failTransactionId, testBudgetId, couponId2, userId, failAmount);

        // Assert
        assertTrue(successResult.success(), "Successful transaction should succeed");
        assertFalse(failResult.success(), "Insufficient budget transaction should fail");
        assertEquals(BudgetErrorCode.NONE, successResult.errorCode());
        assertEquals(BudgetErrorCode.INSUFFICIENT_BUDGET, failResult.errorCode());

        // Verify final state - only successful transaction should be reflected
        Budget finalBudget = budgetRepository.findById(testBudgetId).orElseThrow();
        assertEquals(INITIAL_BUDGET_AMOUNT.subtract(successAmount), finalBudget.getRemaining(), "Remaining should reflect only successful transaction");

        // Verify only successful usage record exists
        assertNotNull(couponBudgetUsageRepository.findByTransactionId(successTransactionId), "Successful transaction record should exist");
        assertNull(couponBudgetUsageRepository.findByTransactionId(failTransactionId), "Failed transaction record should not exist");
        assertEquals(1, couponBudgetUsageRepository.countByBudgetId(testBudgetId), "Only one usage record should exist");
    }
}
