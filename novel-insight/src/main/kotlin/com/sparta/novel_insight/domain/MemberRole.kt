package com.sparta.novel_insight.domain

//
// MemberRole - 회원 역할 구분
//
// 회원은 작가(AUTHOR) 또는 독자(READER) 역할을 가질 수 있습니다.
// - AUTHOR: 소설을 작성하고 게시할 수 있는 작가
// - READER: 소설을 읽고 리뷰/댓글을 작성할 수 있는 독자
//
enum class MemberRole {
    AUTHOR,  // 작가
    READER   // 독자
}
