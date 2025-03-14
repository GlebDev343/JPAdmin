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
 * Custom Hibernate UserType for mapping PostgreSQL BOX type to a Java String.
 * <p>
 * This type maps PostgreSQL BOX columns, which represent rectangular areas defined by two points,
 * to Java strings. The value must be in the format "(x1,y1),(x2,y2)", e.g., "(3,4),(1,2)".
 */
public class BoxType implements UserType<String> {

    private static final int SQL_TYPE = Types.OTHER;
    private static final String BOX_TYPE = "box";
    private static final Pattern BOX_PATTERN = Pattern.compile(
            "^\\(\\s*\\d+\\s*,\\s*\\d+\\s*\\),\\s*\\(\\s*\\d+\\s*,\\s*\\d+\\s*\\)$"
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
            if (!BOX_PATTERN.matcher(value).matches()) {
                throw new HibernateException("Value '" + value + "' must be in format '(x1,y1),(x2,y2)' for BOX type");
            }
            PGobject pgObject = new PGobject();
            pgObject.setType(BOX_TYPE);
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