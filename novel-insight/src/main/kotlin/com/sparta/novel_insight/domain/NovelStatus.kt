package com.sparta.novel_insight.domain

//
// NovelStatus - 소설 연재 상태
//
// 소설의 현재 연재 상태를 나타냅니다.
// - ONGOING: 연재 중 (새로운 에피소드가 계속 업로드됨)
// - COMPLETED: 완결 (모든 에피소드가 업로드 완료됨)
// - HIATUS: 휴재 중 (작가가 일시적으로 연재를 중단함)
//
enum class NovelStatus {
    ONGOING,    // 연재 중
    COMPLETED,  // 완결
    HIATUS      // 휴재 중
}
