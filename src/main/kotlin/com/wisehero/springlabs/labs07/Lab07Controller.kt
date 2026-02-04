package com.wisehero.springlabs.labs07

import com.wisehero.springlabs.common.dto.ApiResponse
import com.wisehero.springlabs.labs07.dto.NplusOneResult
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/experiments")
class Lab07Controller(
    private val nplusOneExperimentService: NplusOneExperimentService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 실험 7-1: @OneToMany N+1 문제 관찰
     * POST /api/v1/experiments/n-plus-one/7-1/basic-n-plus-one
     */
    @PostMapping("/n-plus-one/7-1/basic-n-plus-one")
    fun testBasicNPlusOne(): ResponseEntity<ApiResponse<NplusOneResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 7-1: @OneToMany N+1 문제 관찰                         ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = nplusOneExperimentService.experiment7_1_basicNPlusOne()
        nplusOneExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 7-1 완료: ${result.conclusion}"))
    }

    /**
     * 실험 7-2: JPQL JOIN FETCH로 N+1 해결
     * POST /api/v1/experiments/n-plus-one/7-2/join-fetch
     */
    @PostMapping("/n-plus-one/7-2/join-fetch")
    fun testJoinFetch(): ResponseEntity<ApiResponse<NplusOneResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 7-2: JPQL JOIN FETCH로 N+1 해결                       ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = nplusOneExperimentService.experiment7_2_joinFetch()
        nplusOneExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 7-2 완료: ${result.conclusion}"))
    }

    /**
     * 실험 7-3: @EntityGraph로 N+1 해결
     * POST /api/v1/experiments/n-plus-one/7-3/entity-graph
     */
    @PostMapping("/n-plus-one/7-3/entity-graph")
    fun testEntityGraph(): ResponseEntity<ApiResponse<NplusOneResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 7-3: @EntityGraph로 N+1 해결                          ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = nplusOneExperimentService.experiment7_3_entityGraph()
        nplusOneExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 7-3 완료: ${result.conclusion}"))
    }

    /**
     * 실험 7-4: @ManyToOne N+1 문제 관찰 및 해결
     * POST /api/v1/experiments/n-plus-one/7-4/many-to-one-n-plus-one
     */
    @PostMapping("/n-plus-one/7-4/many-to-one-n-plus-one")
    fun testManyToOneNPlusOne(): ResponseEntity<ApiResponse<NplusOneResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 7-4: @ManyToOne N+1 문제 관찰 및 해결                   ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = nplusOneExperimentService.experiment7_4_manyToOneNPlusOne()
        nplusOneExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 7-4 완료: ${result.conclusion}"))
    }

    /**
     * 실험 7-5: DTO Projection으로 N+1 회피
     * POST /api/v1/experiments/n-plus-one/7-5/dto-projection
     */
    @PostMapping("/n-plus-one/7-5/dto-projection")
    fun testDtoProjection(): ResponseEntity<ApiResponse<NplusOneResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 7-5: DTO Projection으로 N+1 회피                      ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = nplusOneExperimentService.experiment7_5_dtoProjection()
        nplusOneExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 7-5 완료: ${result.conclusion}"))
    }

    /**
     * 실험 7-6: MultipleBagFetchException과 순차 Fetch 해결
     * POST /api/v1/experiments/n-plus-one/7-6/multiple-bag-fetch
     */
    @PostMapping("/n-plus-one/7-6/multiple-bag-fetch")
    fun testMultipleBagFetch(): ResponseEntity<ApiResponse<NplusOneResult>> {
        log.info("")
        log.info("╔════════════════════════════════════════════════════════════╗")
        log.info("║  실험 7-6: MultipleBagFetchException과 순차 Fetch 해결       ║")
        log.info("╚════════════════════════════════════════════════════════════╝")
        log.info("")

        val result = nplusOneExperimentService.experiment7_6_multipleBagFetch()
        nplusOneExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(result, "실험 7-6 완료: ${result.conclusion}"))
    }

    /**
     * Lab 07 테스트 데이터 정리
     * DELETE /api/v1/experiments/n-plus-one/cleanup
     */
    @DeleteMapping("/n-plus-one/cleanup")
    fun cleanupNPlusOneTestData(): ResponseEntity<ApiResponse<Map<String, Int>>> {
        val deleted = nplusOneExperimentService.cleanupTestData()
        return ResponseEntity.ok(ApiResponse.success(
            mapOf("deletedCount" to deleted),
            "Lab 07 테스트 데이터 ${deleted}건 삭제"
        ))
    }
}
