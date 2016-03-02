package org.niord.core.db;

/**
 * Bizarrely, using the proper MySQL56SpatialDialect class (or MySQL56InnoDBSpatialDialect) will cause an error.
 * And, alas, MySQLSpatialDialect will not give very precise results, since is only matches the
 * Minimum Bounding Rectangle (MBR), not the actual shape.
 * <p/>
 * Hence, the class below is based on MySQL's MySQLSpatialDialect, but use the "ST_" versions of the
 * spatial functions.
 */

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.function.StandardSQLFunction;

import org.hibernate.service.ServiceRegistry;
import org.hibernate.spatial.GeolatteGeometryType;
import org.hibernate.spatial.JTSGeometryType;
import org.hibernate.spatial.SpatialDialect;
import org.hibernate.spatial.SpatialFunction;
import org.hibernate.spatial.SpatialRelation;
import org.hibernate.spatial.dialect.mysql.MySQLGeometryTypeDescriptor;
import org.hibernate.type.StandardBasicTypes;

/**
 * A Dialect for MySQL with support for its spatial features
 *
 * @author Karel Maesen, Boni Gopalan
 */
public class MySQLSpatialDialect extends MySQLDialect implements SpatialDialect {

    /**
     * Constructs an instance
     */
    public MySQLSpatialDialect() {
        super();
        registerColumnType(
                MySQLGeometryTypeDescriptor.INSTANCE.getSqlType(),
                "GEOMETRY"
        );
        final MySQLSpatialFunctions functionsToRegister = overrideObjectShapeFunctions( new MySQLSpatialFunctions() );
        for ( Map.Entry<String, StandardSQLFunction> entry : functionsToRegister ) {
            registerFunction( entry.getKey(), entry.getValue() );
        }
    }

    private MySQLSpatialFunctions overrideObjectShapeFunctions(MySQLSpatialFunctions mysqlFunctions) {
        mysqlFunctions.put( "contains", new StandardSQLFunction( "ST_Contains", StandardBasicTypes.BOOLEAN ) );
        mysqlFunctions.put( "crosses", new StandardSQLFunction( "ST_Crosses", StandardBasicTypes.BOOLEAN ) );
        mysqlFunctions.put( "disjoint", new StandardSQLFunction( "ST_Disjoint", StandardBasicTypes.BOOLEAN ) );
        mysqlFunctions.put( "equals", new StandardSQLFunction( "ST_Equals", StandardBasicTypes.BOOLEAN ) );
        mysqlFunctions.put( "intersects", new StandardSQLFunction( "ST_Intersects", StandardBasicTypes.BOOLEAN ) );
        mysqlFunctions.put( "overlaps", new StandardSQLFunction( "ST_Overlaps", StandardBasicTypes.BOOLEAN ) );
        mysqlFunctions.put( "touches", new StandardSQLFunction( "ST_Touches", StandardBasicTypes.BOOLEAN ) );
        mysqlFunctions.put( "within", new StandardSQLFunction( "ST_Within", StandardBasicTypes.BOOLEAN ) );
        return mysqlFunctions;
    }


    @Override
    public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        super.contributeTypes(
                typeContributions,
                serviceRegistry
        );
        typeContributions.contributeType( new GeolatteGeometryType( MySQLGeometryTypeDescriptor.INSTANCE ) );
        typeContributions.contributeType( new JTSGeometryType( MySQLGeometryTypeDescriptor.INSTANCE ) );
    }

    @Override
    public String getSpatialRelateSQL(String columnName, int spatialRelation) {
        switch ( spatialRelation ) {
            case SpatialRelation.WITHIN:
                return " ST_Within(" + columnName + ",?)";
            case SpatialRelation.CONTAINS:
                return " ST_Contains(" + columnName + ", ?)";
            case SpatialRelation.CROSSES:
                return " ST_Crosses(" + columnName + ", ?)";
            case SpatialRelation.OVERLAPS:
                return " ST_Overlaps(" + columnName + ", ?)";
            case SpatialRelation.DISJOINT:
                return " ST_Disjoint(" + columnName + ", ?)";
            case SpatialRelation.INTERSECTS:
                return " ST_Intersects(" + columnName + ", ?)";
            case SpatialRelation.TOUCHES:
                return " ST_Touches(" + columnName + ", ?)";
            case SpatialRelation.EQUALS:
                return " ST_Equals(" + columnName + ", ?)";
            default:
                throw new IllegalArgumentException(
                        "Spatial relation is not known by this dialect"
                );
        }
    }


    @Override
    public String getSpatialFilterExpression(String columnName) {
        return "MBRIntersects(" + columnName + ", ? ) ";
    }

    @Override
    public String getSpatialAggregateSQL(String columnName, int aggregation) {
        throw new UnsupportedOperationException( "Mysql has no spatial aggregate SQL functions." );
    }

    @Override
    public String getDWithinSQL(String columnName) {
        throw new UnsupportedOperationException("Mysql doesn't support the within function");
    }

    @Override
    public String getHavingSridSQL(String columnName) {
        return " (srid(" + columnName + ") = ?) ";
    }

    @Override
    public String getIsEmptySQL(String columnName, boolean isEmpty) {
        final String emptyExpr = " IsEmpty(" + columnName + ") ";
        return isEmpty ? emptyExpr : "( NOT " + emptyExpr + ")";
    }

    @Override
    public boolean supportsFiltering() {
        return false;
    }

    @Override
    public boolean supports(SpatialFunction function) {
        switch ( function ) {
            case boundary:
            case relate:
            case distance:
            case buffer:
            case convexhull:
            case difference:
            case symdifference:
            case intersection:
            case geomunion:
            case dwithin:
            case transform:
                return false;
            default:
                return true;
        }
    }

}

/**
 * An {@code Iterable} over the spatial functions supported by MySQL.
 *
 * @author Karel Maesen, Geovise BVBA
 *
 */
class MySQLSpatialFunctions implements Iterable<Map.Entry<String, StandardSQLFunction>> {

    private final Map<String, StandardSQLFunction> functionsToRegister = new HashMap<>();

    MySQLSpatialFunctions(){
        functionsToRegister.put(
                "dimension", new StandardSQLFunction(
                        "dimension",
                        StandardBasicTypes.INTEGER
                )
        );
        functionsToRegister.put(
                "geometrytype", new StandardSQLFunction(
                        "geometrytype", StandardBasicTypes.STRING
                )
        );
        functionsToRegister.put(
                "srid", new StandardSQLFunction(
                        "srid",
                        StandardBasicTypes.INTEGER
                )
        );
        functionsToRegister.put(
                "envelope", new StandardSQLFunction(
                        "envelope"
                )
        );
        functionsToRegister.put(
                "astext", new StandardSQLFunction(
                        "astext",
                        StandardBasicTypes.STRING
                )
        );
        functionsToRegister.put(
                "asbinary", new StandardSQLFunction(
                        "asbinary",
                        StandardBasicTypes.BINARY
                )
        );
        functionsToRegister.put(
                "isempty", new StandardSQLFunction(
                        "isempty",
                        StandardBasicTypes.BOOLEAN
                )
        );
        functionsToRegister.put(
                "issimple", new StandardSQLFunction(
                        "issimple",
                        StandardBasicTypes.BOOLEAN
                )
        );
        // Register functions for spatial relation constructs
        functionsToRegister.put(
                "overlaps", new StandardSQLFunction(
                        "overlaps",
                        StandardBasicTypes.BOOLEAN
                )
        );
        functionsToRegister.put(
                "intersects", new StandardSQLFunction(
                        "intersects",
                        StandardBasicTypes.BOOLEAN
                )
        );
        functionsToRegister.put(
                "equals", new StandardSQLFunction(
                        "equals",
                        StandardBasicTypes.BOOLEAN
                )
        );
        functionsToRegister.put(
                "contains", new StandardSQLFunction(
                        "contains",
                        StandardBasicTypes.BOOLEAN
                )
        );
        functionsToRegister.put(
                "crosses", new StandardSQLFunction(
                        "crosses",
                        StandardBasicTypes.BOOLEAN
                )
        );
        functionsToRegister.put(
                "disjoint", new StandardSQLFunction(
                        "disjoint",
                        StandardBasicTypes.BOOLEAN
                )
        );
        functionsToRegister.put(
                "touches", new StandardSQLFunction(
                        "touches",
                        StandardBasicTypes.BOOLEAN
                )
        );
        functionsToRegister.put(
                "within", new StandardSQLFunction(
                        "within",
                        StandardBasicTypes.BOOLEAN
                )
        );
    }

    public void put(String name, StandardSQLFunction function ) {
        this.functionsToRegister.put( name, function );
    }

    @Override
    public Iterator<Map.Entry<String, StandardSQLFunction>> iterator() {
        return functionsToRegister.entrySet().iterator();
    }
}
