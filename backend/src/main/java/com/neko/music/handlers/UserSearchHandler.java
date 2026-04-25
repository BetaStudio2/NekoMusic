package com.neko.music.handlers;

import com.neko.music.Main;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class UserSearchHandler extends HttpServlet {
    private static final Logger logger = LoggerFactory.getLogger(UserSearchHandler.class);

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            // 获取查询参数
            String query = request.getParameter("q");
            
            if (query == null || query.trim().isEmpty()) {
                response.setStatus(HttpStatus.BAD_REQUEST_400);
                response.setContentType("application/json;charset=utf-8");
                ErrorResponse errorResponse = new ErrorResponse("查询参数不能为空");
                response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
                return;
            }
            
            // 搜索用户
            List<User> results = searchUsers(query);
            
            // 返回结果
            UserSearchResponse searchResponse = new UserSearchResponse(true, 
                "搜索成功", 
                results);
            
            response.setStatus(HttpStatus.OK_200);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(searchResponse));
            
        } catch (Exception e) {
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR_500);
            response.setContentType("application/json;charset=utf-8");
            ErrorResponse errorResponse = new ErrorResponse("搜索用户时出错: " + e.getMessage());
            response.getWriter().println(Main.getObjectMapper().writeValueAsString(errorResponse));
        }
    }
    
    private List<User> searchUsers(String query) {
        List<User> results = new ArrayList<>();
        
        try (Connection conn = Main.getDatabaseManager().getConnection()) {
            String sql = "SELECT id, username, email, created_at FROM users WHERE username LIKE ? OR email LIKE ? ORDER BY created_at DESC LIMIT 20";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                String likeQuery = "%" + query + "%";
                stmt.setString(1, likeQuery);
                stmt.setString(2, likeQuery);
                
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    User user = new User();
                    user.setId(rs.getInt("id"));
                    user.setUsername(rs.getString("username"));
                    user.setEmail(rs.getString("email"));
                    user.setCreatedAt(rs.getTimestamp("created_at").toString());
                    
                    results.add(user);
                }
            }
        } catch (Exception e) {
            logger.error("搜索用户时出错", e);
        }
        
        return results;
    }
    
    // 内部类用于表示用户对象
    private static class User {
        private int id;
        private String username;
        private String email;
        private String createdAt;
        
        // Getters and Setters
        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }
    
    // 内部类用于表示搜索响应
    private static class UserSearchResponse {
        private boolean success;
        private String message;
        private List<User> users;
        
        public UserSearchResponse(boolean success, String message, List<User> users) {
            this.success = success;
            this.message = message;
            this.users = users;
        }
        
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public List<User> getUsers() { return users; }
        public void setUsers(List<User> users) { this.users = users; }
    }
    
    // 内部类用于表示错误响应
    private static class ErrorResponse {
        private String error;
        
        public ErrorResponse(String error) {
            this.error = error;
        }
        
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
    }
}