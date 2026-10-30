package com.example.duneess;
import java.sql.*;
import java.util.UUID;

/** MySQL 8 / PostgreSQL dialect boundary; UUIDs remain canonical strings on MySQL. */
final class SqlDialect {
 static Connection connect(String url,String user,String password)throws SQLException{
  boolean mysql=url.startsWith("jdbc:mysql:");
  try{Class.forName(mysql?"com.mysql.cj.jdbc.Driver":"org.postgresql.Driver");}catch(ClassNotFoundException e){throw new SQLException("Shared SQL driver unavailable",e);}
  java.util.Properties settings=new java.util.Properties();settings.setProperty("user",user);settings.setProperty("password",password);settings.setProperty("connectTimeout",mysql?"5000":"5");settings.setProperty("socketTimeout",mysql?"10000":"10");
  if(mysql){settings.setProperty("tcpKeepAlive","true");settings.setProperty("autoReconnect","false");settings.setProperty("useAffectedRows","false");settings.setProperty("connectionTimeZone","UTC");}
  return DriverManager.getConnection(url,settings);
 }
 static boolean mysql(Connection c)throws SQLException{
  DatabaseMetaData meta=c.getMetaData();if(meta==null)return false;String product=meta.getDatabaseProductName(),url=meta.getURL();
  if(product!=null&&product.equalsIgnoreCase("H2"))try(Statement q=c.createStatement();ResultSet r=q.executeQuery("SELECT SETTING_VALUE FROM INFORMATION_SCHEMA.SETTINGS WHERE SETTING_NAME='MODE'")){return r.next()&&"MySQL".equalsIgnoreCase(r.getString(1));}
  return product!=null&&product.toLowerCase(java.util.Locale.ROOT).contains("mysql")||url!=null&&url.startsWith("jdbc:mysql:");
 }
 static String sql(Connection c,String sql)throws SQLException{
  if(!mysql(c))return sql;
  String converted=sql.replace("BIGSERIAL PRIMARY KEY","BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY").replace(" UUID "," CHAR(36) ")
   .replace("TIMESTAMP WITH TIME ZONE","TIMESTAMP(3)")
   .replace("ON CONFLICT(uuid) DO NOTHING","ON DUPLICATE KEY UPDATE uuid=uuid")
   .replace("ON CONFLICT(uuid) DO UPDATE SET ","ON DUPLICATE KEY UPDATE ")
   .replace("EXCLUDED.balance","VALUES(balance)");
  converted=converted.replaceAll("\\bmonth\\b","`month`");
  if(converted.startsWith("CREATE TABLE ")&&c.getMetaData().getDatabaseProductName().toLowerCase(java.util.Locale.ROOT).contains("mysql"))converted+=" ENGINE=InnoDB";
  return converted;
 }
 static PreparedStatement prepare(Connection c,String sql)throws SQLException{return c.prepareStatement(sql(c,sql));}
 static void uuid(PreparedStatement q,int index,UUID id)throws SQLException{Connection c=q.getConnection();if(c!=null&&mysql(c))q.setString(index,id.toString());else q.setObject(index,id);}
 static UUID uuid(ResultSet row,int index)throws SQLException{return UUID.fromString(row.getString(index));}
 static UUID uuid(ResultSet row,String column)throws SQLException{return UUID.fromString(row.getString(column));}
 static boolean duplicate(SQLException error){return error.getErrorCode()==1062||"23505".equals(error.getSQLState());}
 static void column(Connection c,Statement s,String table,String column,String definition)throws SQLException{
  if(!mysql(c)){s.executeUpdate("ALTER TABLE "+table+" ADD COLUMN IF NOT EXISTS "+column+" "+definition);return;}
  try(ResultSet rows=c.getMetaData().getColumns(c.getCatalog(),null,table,column)){if(rows.next())return;}
  try{s.executeUpdate("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);}catch(SQLException e){if(e.getErrorCode()!=1060)throw e;}
 }
 static void index(Connection c,Statement s,String name,String table,String columns)throws SQLException{
  if(!mysql(c)){s.executeUpdate("CREATE INDEX IF NOT EXISTS "+name+" ON "+table+"("+columns+")");return;}
  try(ResultSet rows=c.getMetaData().getIndexInfo(c.getCatalog(),null,table,false,false)){while(rows.next())if(name.equalsIgnoreCase(rows.getString("INDEX_NAME")))return;}
  try{s.executeUpdate("CREATE INDEX "+name+" ON "+table+"("+columns+")");}catch(SQLException e){if(e.getErrorCode()!=1061&&e.getErrorCode()!=42111)throw e;}
 }
}
