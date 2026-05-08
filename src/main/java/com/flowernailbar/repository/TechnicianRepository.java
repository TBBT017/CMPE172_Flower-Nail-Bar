package com.flowernailbar.repository;

import com.flowernailbar.model.Technician;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class TechnicianRepository {

    @Autowired
    private JdbcTemplate jdbc;

    private final RowMapper<Technician> rowMapper = (rs, rowNum) -> {
        Technician t = new Technician();
        t.setId(rs.getLong("id"));
        t.setName(rs.getString("name"));
        t.setSpecialty(rs.getString("specialty"));
        t.setLocation(rs.getString("location"));
        return t;
    };

    public List<Technician> findByLocation(String location) {
        return jdbc.query(
            "SELECT * FROM technicians WHERE location = ? ORDER BY name",
            rowMapper, location
        );
    }

    public Optional<Technician> findById(Long id) {
        List<Technician> results = jdbc.query(
            "SELECT * FROM technicians WHERE id = ?",
            rowMapper, id
        );
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
