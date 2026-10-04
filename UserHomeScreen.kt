package com.example.application

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
// 👇 Firebase Realtime Database 및 Messaging 임포트 추가
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging

@Composable
fun UserHomeScreen(
    id: String,
    onLogout: () -> Unit
) {
    val lostItemManager = remember { LostItemManager() }
    var currentSubScreen by remember { mutableStateOf("home") }

    // 화면이 처음 켜질 때(로그인 직후) FCM 토큰을 자동으로 데이터베이스에 등록합니다.
    LaunchedEffect(id) {
        if (id.isNotEmpty()) {
            saveFCMTokenToDatabase(id)
        }
    }

    when (currentSubScreen) {
        "register" -> {
            RegisterItemScreen(
                registerId = id,
                lostItemManager = lostItemManager,
                onBack = { currentSubScreen = "home" },
                onSuccess = { currentSubScreen = "list" }
            )
        }
        "list" -> {
            LostItemListScreen(
                currentUserId = id,
                lostItemManager = lostItemManager,
                onBack = { currentSubScreen = "home" }
            )
        }
        "reward" -> {
            UserRewardScreen(
                currentUserId = id,
                lostItemManager = lostItemManager,
                onBack = { currentSubScreen = "home" }
            )
        }
        "notifications" -> {
            // 🔔 알림함 화면 연결
            NotificationScreen(
                userId = id,
                onBack = { currentSubScreen = "home" }
            )
        }
        else -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(30.dp)
            ) {
                Text(text = "사용자 홈", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "$id 님 환영합니다.")
                Spacer(modifier = Modifier.height(30.dp))

                Button(
                    onClick = { currentSubScreen = "list" },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("분실물 조회 및 소유권 인증")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { currentSubScreen = "register" },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("습득물 등록 (사진 첨부)")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { currentSubScreen = "reward" },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("내 리워드 (보관함 우선 배정권)")
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 🔔 알림함으로 이동하는 버튼 추가
                Button(
                    onClick = { currentSubScreen = "notifications" },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("알림함 확인하기")
                }

                Spacer(modifier = Modifier.height(30.dp))

                OutlinedButton(onClick = { onLogout() }) {
                    Text("로그아웃")
                }
            }
        }
    }
}

// 1. 습득물 등록 폼 (제목, 내용, 위치, 시간, 사진 첨부)
@Composable
fun RegisterItemScreen(
    registerId: String,
    lostItemManager: LostItemManager,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> imageUri = uri }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(text = "습득물 등록", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("물품 이름") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("특징 (색상, 브랜드 등)") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("습득 장소") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("습득 시간") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { launcher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
            Text(if (imageUri == null) "사진 첨부하기" else "사진 첨부 완료 ✓")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (title.isBlank() || location.isBlank()) {
                    Toast.makeText(context, "물품 이름과 장소는 필수 입력입니다.", Toast.LENGTH_SHORT).show()
                } else {
                    lostItemManager.registerLostItem(title, description, location, time, imageUri, registerId) { success, msg ->
                        if (success) {
                            Toast.makeText(context, "분실물 등록 성공!", Toast.LENGTH_SHORT).show()
                            onSuccess()
                        } else {
                            Toast.makeText(context, "등록 실패: $msg", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("등록 완료")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("뒤로 가기")
        }
    }
}

// 2. 분실물 목록 화면 및 소유권 인증 요청
@Composable
fun LostItemListScreen(
    currentUserId: String,
    lostItemManager: LostItemManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var itemList by remember { mutableStateOf<List<LostItem>>(emptyList()) }

    DisposableEffect(Unit) {
        lostItemManager.getLostItems { items -> itemList = items }
        onDispose {}
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(text = "분실물 목록 및 소유권 인증", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(itemList) { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "물품명: ${item.title}", style = MaterialTheme.typography.titleMedium)
                        Text(text = "특징: ${item.description}")
                        Text(text = "장소: ${item.location} / 시간: ${item.time}")
                        Text(text = "상태: ${item.status}", style = MaterialTheme.typography.bodySmall)

                        Spacer(modifier = Modifier.height(8.dp))

                        if (item.status == "REGISTERED") {
                            Button(
                                onClick = {
                                    lostItemManager.requestOwnership(item.id, currentUserId) { success, msg ->
                                        if (success) {
                                            Toast.makeText(context, "소유권 인증 요청 완료!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "요청 실패: $msg", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Text("내 물건 인증 요청하기")
                            }
                        } else if (item.status == "REQUESTED") {
                            Text(text = "관리자 심사 대기 중...", color = MaterialTheme.colorScheme.primary)
                        } else if (item.status == "APPROVED" || item.status == "COMPLETED") {
                            Text(text = "반환 완료 / 승인됨", color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("뒤로 가기")
        }
    }
}

// 3. 마이페이지: 내 리워드(우선 배정권) 확인 UI
@Composable
fun UserRewardScreen(
    currentUserId: String,
    lostItemManager: LostItemManager,
    onBack: () -> Unit
) {
    var rewardCount by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        lostItemManager.getUserReward(currentUserId) { count ->
            rewardCount = count
        }
        onDispose {}
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "내 리워드 마이페이지", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(30.dp))

        Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "보유 중인 보관함 우선 배정권", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "$rewardCount 개", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(30.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("뒤로 가기")
        }
    }
}

/**
 * FCM 토큰을 발급받아 Realtime Database의 사용자 정보 경로에 저장하는 함수
 */
fun saveFCMTokenToDatabase(safeLoginId: String) {
    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        if (!task.isSuccessful) {
            println("❌ FCM 토큰 발급 실패: ${task.exception}")
            return@addOnCompleteListener
        }

        val token = task.result ?: return@addOnCompleteListener
        val databaseRef = FirebaseDatabase.getInstance().getReference("users")

        databaseRef.child(safeLoginId).child("fcmToken").setValue(token)
            .addOnSuccessListener {
                println("✅ FCM 토큰 저장 성공: $token")
            }
            .addOnFailureListener { e ->
                println("❌ FCM 토큰 저장 실패: $e")
            }
    }
}