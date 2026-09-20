package com.neko.music.handlers;

import com.neko.music.model.ErrorResponse;
import com.neko.music.Main;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
            String sql = "SELECT id, nickname, email, created_at FROM users WHERE nickname LIKE ? OR email LIKE ? ORDER BY created_at DESC LIMIT 20";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                String likeQuery = "%" + query + "%";
                stmt.setString(1, likeQuery);
                stmt.setString(2, likeQuery);
                
                ResultSet rs = stmt.executeQuery();
                
                while (rs.next()) {
                    results.add(new User(
                            rs.getInt("id"),
                            rs.getString("nickname"),
                            rs.getString("email"),
                            rs.getTimestamp("created_at").toString()));
                }
            }
        } catch (Exception e) {
            logger.error("搜索用户时出错", e);
        }
        
        return results;
    }
    
    // 内部类用于表示用户对象
    private record User(int id, String nickname, String email, String createdAt) {
    }
    
    // 内部类用于表示搜索响应
    private record UserSearchResponse(boolean success, String message, List<User> users) {
    }
    
    // 内部类用于表示错误响应
}