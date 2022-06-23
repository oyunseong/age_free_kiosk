# 에이지프리 키오스크(Age-Free Kiosk)

## ✌ Topic
<디지털 전환을 이끄는 힘: 소프트웨어><br>
디지털 소외계층을 위한 에이지프리(Age-Free) 키오스크 개발

## ⚡ Key Features
- 에이지프리 키오스크: 연령에 상관없이 모든 사용자가 사용할 수 있는 키오스크
- 키오스크 사용에 있어 불편하지 않게 연령대별 최적화된 UX/UI를 제공
- 소상공인 판매상품과 고객 나이/성별 데이터 저장 및 분석
- 데이터를 기반으로 제품추천을 수행

## 😊 Flow
에이지프리 키오스크 관련 전반적인 구동 플로우입니다.
![aa](https://user-images.githubusercontent.com/22411406/175378328-23520d9a-452f-4c55-8977-0192b8337a61.png)

## 👊 Introduction
에이지프리 키오스크 관련 실제 구동 화면입니다.<br>

"TODO"

## 😃 Client: Package
클라이언트에서 사용한 패키지 관련 설명입니다.

### - adapter: ㅇㅇㅇ을 위한 Adapter Package
- dddAdapter: ㅇㅇ을 하는 Adapter

"TODO"

## 🌕 Server: API Architecture
서버에서 사용한 API 아키텍쳐 관련 설명입니다.

### - POST /v1/detect: 클라이언트에서 사진을 보냈을 때 연령, 성별을 알려주는 API
- 내부적으로 Kakao Vision API를 call 하면서 동작함
- 기존 다른 머신러닝 모델들(FaceNet)과 비교했을 때 정확도가 더 높아 선택
- 연령, 성별을 클라이언트로 내려주어 클라이언트에서 적절한 UI를 보여주는데 사용

### - POST /v1/order: 클라이언트에서 서버로 주문을 보내는 API
- 연령, 성별, 메뉴, 메뉴 추가 정보, 수량등을 서버로 전송
- 서버에서 통계를 저장해 나중에 데이터 분석으로 사용할 수 있도록 하고, 주문을 저장함

### - GET /v1/order: 클라이언트에서 서버에 존재하는 주문 목록을 가져오는 API
- 연령, 성별, 메뉴, 옵션 등 정보를 서버에서 가져옴
- 이를 바탕으로 매장 내 직원이 음식을 만들고 서빙을 진행

### - GET /v1/best/{age}/{gender}: 내 나이와 성별에 맞는 Best Item 추천 API
- 내 연령과 성별을 Query Parameter로 넣어주어 서버로 전송함
- 서버에서는 기존에 쌓인 데이터를 기반으로 적절히 음식을 추천해줌(간단한 예시: 60대 여성의 경우 카모마일티)

## 🐔 Tech Stack
- Client: Kotlin, Android, OpenCV, Android Studio
- Server: Python Flask, MySQL, Kakao Vision API, Swagger

## ☀ Team
- Team 에이지프리
- 김영우, 이준수, 최승호, 오윤성, 유혜린
