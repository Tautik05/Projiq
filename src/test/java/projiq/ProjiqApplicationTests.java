package projiq;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ProjiqApplicationTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Test
	void contextLoads() {
		assertNotNull(dataSource, "DataSource should be configured");
		assertNotNull(redisTemplate, "RedisTemplate should be configured");
	}

	@Test
	void testPostgresConnectivity() throws Exception {
		try (Connection connection = dataSource.getConnection();
			 Statement statement = connection.createStatement();
			 ResultSet resultSet = statement.executeQuery("SELECT 1")) {
			assertTrue(resultSet.next(), "Database query should return a row");
			assertEquals(1, resultSet.getInt(1), "Database query result should be 1");
		}
	}

	@Test
	void testUpstashRedisConnectivity() {
		String testKey = "projiq:test:ping";
		String testValue = "pong_" + System.currentTimeMillis();
		redisTemplate.opsForValue().set(testKey, testValue);
		String retrieved = redisTemplate.opsForValue().get(testKey);
		assertEquals(testValue, retrieved, "Redis key value should match what was set");
		redisTemplate.delete(testKey);
	}
}
