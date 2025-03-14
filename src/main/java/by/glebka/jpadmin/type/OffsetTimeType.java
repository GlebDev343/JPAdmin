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
import java.time.OffsetTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Objects;

/**
 * Custom Hibernate UserType for mapping PostgreSQL TIMETZ type to a Java OffsetTime.
 * <p>
 * This type maps PostgreSQL TIMETZ (time with time zone) columns to Java OffsetTime objects.
 * It supports parsing time strings with offsets and optional nanoseconds, e.g., "13:45:00+02:00" or "09:30:15.123+00".
 */
public class OffsetTimeType implements UserType<OffsetTime> {

    private static final int SQL_TYPE = Types.OTHER;
    private static final String TIMETZ_TYPE = "timetz";
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("HH:mm:ss")
            .appendOffset("+HH:mm", "+HH")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .toFormatter();
    private static final DateTimeFormatter OUTPUT_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ssXXX");

    @Override
    public int getSqlType() {
        return SQL_TYPE;
    }

    @Override
    public Class<OffsetTime> returnedClass() {
        return OffsetTime.class;
    }

    @Override
    public boolean equals(OffsetTime x, OffsetTime y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(OffsetTime x) {
        return Objects.hashCode(x);
    }

    @Override
    public OffsetTime nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner)
            throws SQLException {
        String value = rs.getString(position);
        if (value == null) {
            return null;
        }
        try {
            return OffsetTime.parse(value, FORMATTER);
        } catch (Exception e) {
            throw new SQLException("Failed to parse OffsetTime from value: " + value, e);
        }
    }

    @Override
    public void nullSafeSet(PreparedStatement st, OffsetTime value, int index, SharedSessionContractImplementor session)
            throws SQLException {
        if (value == null) {
            st.setNull(index, SQL_TYPE);
        } else {
            PGobject pgObject = new PGobject();
            pgObject.setType(TIMETZ_TYPE);
            pgObject.setValue(value.format(OUTPUT_FORMATTER));
            st.setObject(index, pgObject, SQL_TYPE);
        }
    }

    @Override
    public OffsetTime deepCopy(OffsetTime value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(OffsetTime value) {
        return value;
    }

    @Override
    public OffsetTime assemble(Serializable cached, Object owner) {
        return (OffsetTime) cached;
    }

    @Override
    public OffsetTime replace(OffsetTime original, OffsetTime target, Object owner) {
        return original;
    }
}