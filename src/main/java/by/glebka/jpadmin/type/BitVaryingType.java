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
 * Custom Hibernate UserType for mapping PostgreSQL BIT VARYING (VARBIT) type to a Java String.
 * <p>
 * This type maps PostgreSQL VARBIT columns, which store variable-length bit strings, to Java strings.
 * The value must consist only of '0' and '1' characters, e.g., "101" or "11000".
 */
public class BitVaryingType implements UserType<String> {

    private static final int SQL_TYPE = Types.OTHER;
    private static final String VARBIT_TYPE = "varbit";
    private static final String BINARY_PATTERN = "^[01]+$";

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
            if (!value.matches(BINARY_PATTERN)) {
                throw new HibernateException("Value '" + value + "' must be a binary string of 0s and 1s");
            }
            PGobject pgObject = new PGobject();
            pgObject.setType(VARBIT_TYPE);
            pgObject.setValue(value);
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