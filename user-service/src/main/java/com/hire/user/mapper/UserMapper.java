package com.hire.user.mapper;
import com.hire.user.model.UserRecord;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
public interface UserMapper {
    String COLUMNS = "id, username, password, real_name AS realName, email, phone, avatar, role, "
            + "status, last_login_time AS lastLoginTime, create_time AS createTime, update_time AS updateTime";
    String FILTER = " FROM t_user WHERE deleted=0 "
            + "<if test='role != null'> AND role=#{role}</if>"
            + "<if test='status != null'> AND status=#{status}</if>"
            + "<if test='keyword != null'> AND (username LIKE CONCAT('%',#{keyword},'%')"
            + " OR real_name LIKE CONCAT('%',#{keyword},'%') OR email LIKE CONCAT('%',#{keyword},'%'))</if>";
    @Select("SELECT " + COLUMNS + " FROM t_user WHERE deleted=0 AND "
            + "(username=#{account} OR email=#{account} OR phone=#{account}) ORDER BY id LIMIT 1")
    UserRecord findByAccount(String account);
    @Select("SELECT " + COLUMNS + " FROM t_user WHERE id=#{id} AND deleted=0")
    UserRecord findById(Long id);
    @Select("SELECT COUNT(*) FROM t_user WHERE username=#{username}")
    int countByUsername(String username);
    @Select("SELECT COUNT(*) FROM t_user WHERE email=#{email} AND (#{excludeId} IS NULL OR id != #{excludeId})")
    int countByEmail(@Param("email") String email, @Param("excludeId") Long excludeId);
    @Insert("INSERT INTO t_user (id,username,password,real_name,email,phone,role,status,deleted,create_time,update_time)"
            + " VALUES (#{id},#{username},#{password},#{realName},#{email},#{phone},#{role},1,0,#{createTime},#{updateTime})")
    void insert(UserRecord user);
    @Update("<script>UPDATE t_user <set>"
            + "<if test='realName != null'>real_name=#{realName},</if>"
            + "<if test='email != null'>email=#{email},</if>"
            + "<if test='phone != null'>phone=#{phone},</if>"
            + "<if test='avatar != null'>avatar=#{avatar},</if>"
            + "update_time=#{updateTime}</set> WHERE id=#{id} AND deleted=0 AND status=1</script>")
    int updateUser(UserRecord user);
    @Update("UPDATE t_user SET password=#{password}, update_time=#{now}"
            + " WHERE id=#{id} AND password=#{oldHash} AND deleted=0 AND status=1")
    int updatePassword(@Param("id") Long id, @Param("oldHash") String oldHash,
                       @Param("password") String password, @Param("now") LocalDateTime now);
    @Update("UPDATE t_user SET last_login_time=#{now} WHERE id=#{id} AND deleted=0 AND status=1")
    int updateLoginTime(@Param("id") Long id, @Param("now") LocalDateTime now);
    @Select("<script>SELECT COUNT(*)" + FILTER + "</script>")
    long countUsers(@Param("role") String role, @Param("status") Integer status, @Param("keyword") String keyword);
    @Select("<script>SELECT " + COLUMNS + FILTER + " ORDER BY create_time DESC,id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<UserRecord> listUsers(@Param("role") String role, @Param("status") Integer status,
                              @Param("keyword") String keyword, @Param("offset") long offset, @Param("size") int size);
    @Update("UPDATE t_user SET status=#{status}, update_time=#{now} WHERE id=#{id} AND deleted=0")
    int updateStatus(@Param("id") Long id, @Param("status") int status, @Param("now") LocalDateTime now);
    @Select("SELECT id FROM t_user WHERE deleted=0 AND status=1 AND role IN ('seeker','employer')"
            + " AND id > #{afterId} ORDER BY id ASC LIMIT #{size}")
    List<Long> notificationRecipients(@Param("afterId") long afterId, @Param("size") int size);
    @Select("SELECT COUNT(*) FROM t_user WHERE deleted=0 AND role IN ('seeker','employer')")
    long registeredUserTotal();
    @Select("SELECT COUNT(*) FROM t_user WHERE deleted=0 AND role='employer'")
    long enterpriseTotal();
    // Existing create_time is interpreted as an Asia/Shanghai local DATETIME.
    @Select("SELECT DATE_FORMAT(create_time,'%Y-%m-%d') AS date, COUNT(*) AS registered,"
            + " SUM(CASE WHEN role='employer' THEN 1 ELSE 0 END) AS enterprises"
            + " FROM t_user WHERE deleted=0 AND role IN ('seeker','employer')"
            + " AND create_time >= #{start} AND create_time < #{end}"
            + " GROUP BY DATE_FORMAT(create_time,'%Y-%m-%d') ORDER BY date")
    List<Map<String,Object>> dailyUsers(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
