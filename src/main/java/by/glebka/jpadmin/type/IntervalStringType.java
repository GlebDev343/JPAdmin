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
 * Custom Hibernate UserType for mapping PostgreSQL INTERVAL type to a Java String.
 * <p>
 * This type maps PostgreSQL INTERVAL columns, which represent a time duration,
 * to Java strings. The value can include units like "years", "days", "hours", or a time format,
 * e.g., "1 day 2 hours", "01:30:00", or "2 days 03:00:00".
 */
public class IntervalStringType implements UserType<String> {

    private static final int SQL_TYPE = Types.OTHER;
    private static final String INTERVAL_TYPE = "interval";
    private static final Pattern INTERVAL_PATTERN = Pattern.compile(
            "^(?:\\d+\\s*(?:years?|months?|days?|hours?|minutes?|seconds?)" +
                    "(?:\\s+\\d+\\s*(?:years?|months?|days?|hours?|minutes?|seconds?))*" +
                    "(?:\\s+\\d{1,2}:\\d{2}:\\d{2})?)?$|^\\d{1,2}:\\d{2}:\\d{2}$"
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
            if (!INTERVAL_PATTERN.matcher(trimmedValue).matches()) {
                throw new HibernateException("Value '" + trimmedValue + "' must be a valid INTERVAL string (e.g., '1 day 2 hours', '01:30:00', or '2 days 03:00:00')");
            }
            PGobject pgObject = new PGobject();
            pgObject.setType(INTERVAL_TYPE);
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