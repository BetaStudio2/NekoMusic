package com.neko.music.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import android.util.Log

class UserApi {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                prettyPrint = true
                isLenient = true
            })
        }
    }

    private val baseUrl = "https://music.cnmsb.xin"

    /**
     * 用户登录
     */
    suspend fun login(username: String, password: String): LoginResponse {
        return try {
            val response = client.post("$baseUrl/api/user/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username = username, password = password))
            }
            response.body()
        } catch (e: Exception) {
            Log.e("UserApi", "登录失败", e)
            LoginResponse(success = false, message = "网络错误: ${e.message}", data = null)
        }
    }

    /**
     * 用户注册
     */
    suspend fun register(username: String, password: String, email: String, verificationCode: String): RegisterResponse {
        return try {
            val response = client.post("$baseUrl/api/user/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(
                    username = username,
                    password = password,
                    email = email,
                    verificationCode = verificationCode
                ))
            }
            response.body()
        } catch (e: Exception) {
            Log.e("UserApi", "注册失败", e)
            RegisterResponse(success = false, message = "网络错误: ${e.message}", data = null)
        }
    }

    /**
     * 发送验证码
     */
    suspend fun sendVerificationCode(email: String, username: String): VerificationResponse {
        return try {
            val response = client.post("$baseUrl/api/user/send-verification") {
                contentType(ContentType.Application.Json)
                setBody(VerificationRequest(email = email, username = username))
            }
            response.body()
        } catch (e: Exception) {
            Log.e("UserApi", "发送验证码失败", e)
            VerificationResponse(success = false, message = "网络错误: ${e.message}", data = null)
        }
    }
}

// 数据模型
@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String,
    val verificationCode: String
)

@Serializable
data class VerificationRequest(
    val email: String,
    val username: String
)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val message: String,
    val data: LoginData?
)

@Serializable
data class LoginData(
    val user: UserData,
    val token: String
)

@Serializable
data class UserData(
    val id: Int,
    val username: String,
    val email: String,
    val createdAt: String
)

@Serializable
data class RegisterResponse(
    val success: Boolean,
    val message: String,
    val data: Map<String, String>?
)

@Serializable
data class VerificationResponse(
    val success: Boolean,
    val message: String,
    val data: String?
)