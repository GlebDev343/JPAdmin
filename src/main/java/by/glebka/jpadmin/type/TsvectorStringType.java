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

/**
 * Custom Hibernate UserType for mapping PostgreSQL TSVECTOR type to a Java String.
 * <p>
 * This type allows storing PostgreSQL full-text search vectors (TSVECTOR) as strings in JPA entities.
 * The value must be a valid TSVECTOR expression, such as "'fat' 'cat'" or "'fat':1 'cat':2".
 */
public class TsvectorStringType implements UserType<String> {

    private static final int SQL_TYPE = Types.OTHER;
    private static final String TSVECTOR_TYPE = "tsvector";

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
            if (trimmedValue.isEmpty()) {
                throw new HibernateException("TSVECTOR value cannot be empty");
            }
            PGobject pgObject = new PGobject();
            pgObject.setType(TSVECTOR_TYPE);
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