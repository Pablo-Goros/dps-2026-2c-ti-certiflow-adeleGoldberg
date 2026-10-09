package ar.edu.itba.dps.certification.infrastructure.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;

@FunctionalInterface
interface RowMapper<T> {

    T map(ResultSet row) throws SQLException;
}
