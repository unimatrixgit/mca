package com.example.duneess;
import java.sql.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;
class SqlTimestampTest {
 @Test void mysqlFractionalTimestampHasMatchingDefaultPrecision()throws Exception{Connection c=mock(Connection.class);DatabaseMetaData m=mock(DatabaseMetaData.class);when(c.getMetaData()).thenReturn(m);when(m.getDatabaseProductName()).thenReturn("MySQL");String s=SqlDialect.sql(c,"CREATE TABLE sample (sold_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP)");assertTrue(s.contains("TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)"));assertTrue(s.endsWith("ENGINE=InnoDB"));}
 @Test void postgresDefinitionIsUnchanged()throws Exception{Connection c=mock(Connection.class);DatabaseMetaData m=mock(DatabaseMetaData.class);when(c.getMetaData()).thenReturn(m);when(m.getDatabaseProductName()).thenReturn("PostgreSQL");String s="CREATE TABLE sample (sold_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP)";assertEquals(s,SqlDialect.sql(c,s));}
}
