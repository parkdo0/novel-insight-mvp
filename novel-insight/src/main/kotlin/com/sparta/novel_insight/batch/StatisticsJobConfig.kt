package com.sparta.novel_insight.batch

import com.sparta.novel_insight.domain.DailyStatistics
import com.sparta.novel_insight.domain.Novel
import com.sparta.novel_insight.repository.DailyStatisticsRepository // (없으면 만들어야 함, 아래 설명)
import jakarta.persistence.EntityManagerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemWriter
import org.springframework.batch.item.database.JpaItemWriter
import org.springframework.batch.item.database.JpaPagingItemReader
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class StatisticsJobConfig(
    private val entityManagerFactory: EntityManagerFactory
) {

    // 1. Job 정의 ("이 작업의 이름은 'statisticsJob' 입니다")
    @Bean
    fun statisticsJob(jobRepository: JobRepository, step: Step): Job {
        return JobBuilder("statisticsJob", jobRepository)
            .start(step) // 첫 번째 단계 실행
            .build()
    }

    // 2. Step 정의 ("1000개씩 끊어서 읽고 쓸 겁니다")
    @Bean
    fun statisticsStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager
    ): Step {
        return StepBuilder("statisticsStep", jobRepository)
            .chunk<Novel, Novel>(1000, transactionManager) // 1000개 단위로 커밋 (메모리 보호)
            .reader(reader())      // 읽어오기
            .processor(processor()) // 가공하기 (여기선 그냥 Pass)
            .writer(writer())      // 출력하기 (콘솔에 찍기)
            .build()
    }

    // 3. Reader: DB에서 소설을 1000개씩 퍼옵니다. (페이징 자동 처리)
    @Bean
    fun reader(): JpaPagingItemReader<Novel> {
        return JpaPagingItemReaderBuilder<Novel>()
            .name("novelReader")
            .entityManagerFactory(entityManagerFactory)
            .queryString("SELECT n FROM Novel n") // 모든 소설 조회
            .pageSize(1000)
            .build()
    }

    // 4. Processor: 데이터를 가공합니다.
    // (원래는 여기서 계산을 해야 하는데, 구조상 Writer에서 합계 내는 게 편해서 일단 통과시킵니다)
    @Bean
    fun processor(): ItemProcessor<Novel, Novel> {
        return ItemProcessor { novel ->
            // 예: 조회수가 0인 건 제외한다거나 하는 로직을 넣을 수 있음
            novel
        }
    }

    // 5. Writer: 1000개씩 받아서 처리합니다.
    // (실무에선 DB에 저장하지만, 확인을 위해 로그만 찍겠습니다)
    // [수정] JpaItemWriter 대신 ItemWriter 인터페이스 직접 구현
    @Bean
    fun writer(): ItemWriter<Novel> {
        return ItemWriter { novels ->
            // 그냥 로그만 찍습니다. (DB 저장 시도 X -> 에러 안 남)
            println("📝 현재 Chunk 처리 중... 개수: ${novels.size()}")

            // 만약 진짜로 DB에 저장하고 싶다면 여기서 DailyStatistics를 save하면 됩니다.
            // novelRepository.saveAll(novels) <-- 이건 업데이트가 됨
        }
    }
}