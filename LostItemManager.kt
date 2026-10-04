package com.example.application

import android.net.Uri
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import java.util.UUID

class LostItemManager {

    private val databaseRef = FirebaseDatabase.getInstance().getReference("lost_items")
    private val storageRef = FirebaseStorage.getInstance().reference

    // 1. 분실물 등록 (사진이 있는 경우 Storage 업로드 후 Realtime Database에 저장)
    fun registerLostItem(
        title: String,
        description: String,
        location: String,
        time: String,
        imageUri: Uri?,
        registerId: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val itemId = databaseRef.push().key ?: UUID.randomUUID().toString()

        if (imageUri != null) {
            // 이미지가 있는 경우 Storage에 업로드
            val imageRef = storageRef.child("lost_images/$itemId.jpg")
            imageRef.putFile(imageUri)
                .addOnSuccessListener {
                    imageRef.downloadUrl.addOnSuccessListener { uri ->
                        saveItemToDatabase(itemId, title, description, location, time, uri.toString(), registerId, onResult)
                    }.addOnFailureListener { e ->
                        onResult(false, "이미지 URL을 가져오는데 실패했습니다: ${e.message}")
                    }
                }
                .addOnFailureListener { e ->
                    onResult(false, "이미지 업로드 실패: ${e.message}")
                }
        } else {
            // 이미지 없이 텍스트만 등록하는 경우
            saveItemToDatabase(itemId, title, description, location, time, "", registerId, onResult)
        }
    }

    private fun saveItemToDatabase(
        itemId: String,
        title: String,
        description: String,
        location: String,
        time: String,
        imageUrl: String,
        registerId: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val item = LostItem(itemId, title, description, location, time, imageUrl, registerId)
        databaseRef.child(itemId).setValue(item)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                onResult(false, "데이터베이스 저장 실패: ${e.message}")
            }
    }

    // 2. 분실물 목록 실시간 조회
    fun getLostItems(onDataChanged: (List<LostItem>) -> Unit) {
        databaseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val itemList = mutableListOf<LostItem>()
                for (child in snapshot.children) {
                    val item = child.getValue(LostItem::class.java)
                    if (item != null) {
                        itemList.add(item)
                    }
                }
                onDataChanged(itemList)
            }

            override fun onCancelled(error: DatabaseError) {
                // 오류 발생 시 예외 처리
            }
        })
    }

    // 3. 소유권 인증 요청하기 (사용자)
    fun requestOwnership(
        itemId: String,
        requesterId: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val updates = mapOf(
            "status" to "REQUESTED",
            "requesterId" to requesterId
        )
        databaseRef.child(itemId).updateChildren(updates)
            .addOnSuccessListener { onResult(true, null) }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

    // 4. 소유권 승인하기 (관리자) + 알림 데이터 및 FCM 토큰 연동
    fun approveOwnership(
        itemId: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val updates = mapOf(
            "status" to "APPROVED"
        )
        databaseRef.child(itemId).updateChildren(updates)
            .addOnSuccessListener {
                // 승인된 물품의 정보를 읽어와서 요청자(requesterId)에게 실시간 알림 데이터 저장 및 FCM 토큰 확인
                databaseRef.child(itemId).get().addOnSuccessListener { snapshot ->
                    val requesterId = snapshot.child("requesterId").getValue(String::class.java)
                    if (!requesterId.isNullOrEmpty()) {
                        val rootRef = FirebaseDatabase.getInstance().reference

                        // 1. 인앱 알림 데이터 저장
                        val notificationId = rootRef.child("notifications").child(requesterId).push().key ?: "notif"
                        val notificationData = mapOf(
                            "title" to "분실물 소유권 승인 완료",
                            "message" to "요청하신 분실물의 소유권 인증이 관리자에 의해 승인되었습니다!",
                            "timestamp" to System.currentTimeMillis()
                        )
                        rootRef.child("notifications").child(requesterId).child(notificationId).setValue(notificationData)

                        // 2. Firebase 경로 제약에 맞게 마침표를 쉼표로 변환하여 FCM 토큰 조회
                        val safeRequesterId = requesterId.replace(".", ",")
                        rootRef.child("users").child(safeRequesterId).child("fcmToken").get()
                            .addOnSuccessListener { tokenSnapshot ->
                                val fcmToken = tokenSnapshot.getValue(String::class.java)
                                if (!fcmToken.isNullOrEmpty()) {
                                    // 가져온 토큰을 활용해 필요시 로그 출력 또는 추가 전송 로직 수행 가능
                                }
                            }
                    }
                    onResult(true, null)
                }.addOnFailureListener {
                    // 상태 변경은 성공했으므로 결과는 성공으로 처리
                    onResult(true, null)
                }
            }
            .addOnFailureListener { e -> onResult(false, e.message) }
    }

    // 5. 반환 완료 및 리워드(우선 배정권) 지급 처리 (관리자)
    fun completeReturnAndReward(
        itemId: String,
        registerId: String,
        rewardGiven: Boolean,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (rewardGiven) {
            onResult(true, "이미 리워드가 지급된 물품입니다.")
            return
        }

        val itemUpdates = mapOf(
            "status" to "COMPLETED",
            "rewardGiven" to true
        )

        databaseRef.child(itemId).updateChildren(itemUpdates)
            .addOnSuccessListener {
                val userRewardRef = FirebaseDatabase.getInstance().getReference("users/$registerId/rewardCount")
                userRewardRef.get().addOnSuccessListener { snapshot ->
                    val currentCount = snapshot.getValue(Int::class.java) ?: 0
                    userRewardRef.setValue(currentCount + 1)
                        .addOnSuccessListener { onResult(true, null) }
                        .addOnFailureListener { e -> onResult(false, "리워드 지급 실패: ${e.message}") }
                }.addOnFailureListener { e ->
                    onResult(false, "사용자 정보 조회 실패: ${e.message}")
                }
            }
            .addOnFailureListener { e ->
                onResult(false, "상태 업데이트 실패: ${e.message}")
            }
    }

    // 6. 사용자의 보유 리워드(우선 배정권) 조회
    fun getUserReward(userId: String, onRewardFetched: (Int) -> Unit) {
        val userRewardRef = FirebaseDatabase.getInstance().getReference("users/$userId/rewardCount")
        userRewardRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.getValue(Int::class.java) ?: 0
                onRewardFetched(count)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}