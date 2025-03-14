package by.glebka.jpadmin.type;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;
import org.postgresql.util.PGobject;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Custom Hibernate UserType for mapping PostgreSQL PATH type to a Java String.
 * <p>
 * This type maps PostgreSQL PATH columns, which represent an open or closed sequence of points,
 * to Java strings. The value must be in the format "[(x1,y1),(x2,y2),...]" (open) or "((x1,y1),(x2,y2),...)" (closed),
 * e.g., "[(1,2),(3,4)]" or "((1,2),(3,4))".
 */
public class PathStringType implements UserType<String> {

    private static final int SQL_TYPE = Types.OTHER;
    private static final String PATH_TYPE = "path";
    private static final Pattern PATH_PATTERN = Pattern.compile(
            "^(\\[|\\()\\s*\\(\\s*[-+]?\\d*\\.?\\d+\\s*,\\s*[-+]?\\d*\\.?\\d+\\s*\\)" +
                    "(?:\\s*,\\s*\\(\\s*[-+]?\\d*\\.?\\d+\\s*,\\s*[-+]?\\d*\\.?\\d+\\s*\\))*\\s*(\\]|\\))$"
    );

    @Override
    public int getSqlType() {
        return SQL_TYPE;
    }

    @Override
    public Class<String> returnedClass() {
        return String.class;
    }

    @Override
    public boolean equals(String x, String y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(String x) {
        return Objects.hashCode(x);
    }

    @Override
    public String nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        String value = rs.getString(position);
        return rs.wasNull() ? null : value;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, String value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        if (value == null) {
            st.setNull(index, SQL_TYPE);
        } else {
            String trimmedValue = value.trim();
            if (!PATH_PATTERN.matcher(trimmedValue).matches()) {
                throw new HibernateException("Value '" + trimmedValue + "' must be a valid PATH string (e.g., '[(1,2),(3,4)]' or '((1,2),(3,4))')");
            }
            PGobject pgObject = new PGobject();
            pgObject.setType(PATH_TYPE);
            pgObject.setValue(trimmedValue);
            st.setObject(index, pgObject, SQL_TYPE);
        }
    }

    @Override
    public String deepCopy(String value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(String value) {
        return value;
    }

    @Override
    public String assemble(Serializable cached, Object owner) {
        return (String) cached;
    }

    @Override
    public String replace(String original, String target, Object owner) {
        return original;
    }
}