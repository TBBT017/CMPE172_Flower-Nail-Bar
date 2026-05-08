package com.flowernailbar.repository;

import com.flowernailbar.model.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ServiceRepository — raw JDBC access to the services table.
 */
@Repository
public class ServiceRepository {

    private static final Logger log = LoggerFactory.getLogger(ServiceRepository.class);

    @Autowired
    private JdbcTemplate jdbc;

    private final RowMapper<Service> serviceRowMapper = (rs, rowNum) -> {
        Service s = new Service();
        s.setId(rs.getLong("id"));
        s.setName(rs.getString("name"));
        s.setDurationMin(rs.getInt("duration_min"));
        s.setPrice(rs.getDouble("price"));
        s.setLocation(rs.getString("location"));
        return s;
    };

    public List<Service> findAll() {
        return jdbc.query("SELECT * FROM services ORDER BY id ASC", serviceRowMapper);
    }

    public List<Service> findByLocation(String location) {
        return jdbc.query(
            "SELECT * FROM services WHERE location = ? ORDER BY id ASC",
            serviceRowMapper, location
        );
    }

    public Optional<Service> findById(Long id) {
        List<Service> results = jdbc.query(
            "SELECT * FROM services WHERE id = ?",
            serviceRowMapper, id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
