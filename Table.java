
/****************************************************************************************
 * @file  Table.java
 *
 * @author   John Miller
 *
 * compile javac *.java
 * run     java MovieDB    
 */

import java.io.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import static java.lang.Boolean.*;
import static java.lang.System.arraycopy;
import static java.lang.System.out;

/****************************************************************************************
 * The Table class implements relational database tables (including attribute
 * names, domains
 * and a list of tuples. Five basic relational algebra operators are provided:
 * project,
 * select, union, minus and join. The insert data manipulation operator is also
 * provided.
 * Missing are update and delete data manipulation operators.
 */
public class Table
        implements Serializable {
    /**
     * Relative path for storage directory
     */
    private static final String DIR = "store" + File.separator;

    /**
     * Filename extension for database files
     */
    private static final String EXT = ".dbf";

    /**
     * Counter for naming temporary tables.
     */
    private static int count = 0;

    /**
     * Table name.
     */
    private final String name;

    /**
     * Array of attribute names.
     */
    private final String[] attribute;

    /**
     * Array of attribute domains: a domain may be
     * integer types: Long, Integer, Short, Byte
     * real types: Double, Float
     * string types: Character, String
     */
    private final Class[] domain;

    /**
     * Collection of tuples (data storage).
     */
    private final List<Comparable[]> tuples;

    /**
     * Primary key (the attributes forming).
     */
    private final String[] key;

    /**
     * Index into tuples (maps key to tuple).
     */
    private final Map<KeyType, Comparable[]> index;

    /**
     * The supported map types.
     */
    private enum MapType {
        NO_MAP, TREE_MAP, HASH_MAP
    }

    /**
     * The map type to be used for indices. Change as needed.
     */
    private static final MapType mType = MapType.NO_MAP;

    /************************************************************************************
     * Make a map (index) given the MapType.
     */
    private static Map<KeyType, Comparable[]> makeMap() {
        return switch (mType) {
            case NO_MAP -> null;
            case TREE_MAP -> new TreeMap<>();
            //case HASH_MAP -> new HashMap<>();
            // Project 2
            case HASH_MAP -> new LinHashMap <> (KeyType.class, Comparable [].class);
            // case BPTREE_MAP -> new BpTreeMap <> (KeyType.class, Comparable [].class);
            default -> null;
        }; // switch
    } // makeMap

    /************************************************************************************
     * Concatenate two arrays of type T to form a new wider array.
     *
     * @see http://stackoverflow.com/questions/80476/how-to-concatenate-two-arrays-in-java
     *
     * @param arr1 the first array
     * @param arr2 the second array
     * @return a wider array containing all the values from arr1 and arr2
     */
    public static <T> T[] concat(T[] arr1, T[] arr2) {
        T[] result = Arrays.copyOf(arr1, arr1.length + arr2.length);
        arraycopy(arr2, 0, result, arr1.length, arr2.length);
        return result;
    } // concat

    // -----------------------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------------------

    /************************************************************************************
     * Construct an empty table from the meta-data specifications.
     *
     * @param _name      the name of the relation
     * @param _attribute the string containing attributes names
     * @param _domain    the string containing attribute domains (data types)
     * @param _key       the primary key
     */
    public Table(String _name, String[] _attribute, Class[] _domain, String[] _key) {
        name = _name;
        attribute = _attribute;
        domain = _domain;
        key = _key;
        tuples = new ArrayList<>();
        // Ensure index is always initialized (prevent NullPointerException)
        index = (mType == MapType.NO_MAP) ? new HashMap<>() : makeMap();
        out.println(Arrays.toString(domain));
    } // constructor

    /************************************************************************************
     * Construct a table from the meta-data specifications and data in _tuples list.
     *
     * @param _name      the name of the relation
     * @param _attribute the string containing attributes names
     * @param _domain    the string containing attribute domains (data types)
     * @param _key       the primary key
     * @param _tuples    the list of tuples containing the data
     */
    public Table(String _name, String[] _attribute, Class[] _domain, String[] _key,
            List<Comparable[]> _tuples) {
        name = _name;
        attribute = _attribute;
        domain = _domain;
        key = _key;
        tuples = _tuples;
        index = makeMap();
    } // constructor

    /************************************************************************************
     * Construct an empty table from the raw string specifications.
     *
     * @param _name      the name of the relation
     * @param attributes the string containing attributes names
     * @param domains    the string containing attribute domains (data types)
     * @param _key       the primary key
     */
    public Table(String _name, String attributes, String domains, String _key) {
        this(_name, attributes.split(" "), findClass(domains.split(" ")), _key.split(" "));

        out.println("DDL> create table " + name + " (" + attributes + ")");
    } // constructor

    // ----------------------------------------------------------------------------------
    // Public Methods
    // ----------------------------------------------------------------------------------

    /************************************************************************************
 * Create an index for this table based on the primary key.
 * This index allows fast lookups but does not enforce uniqueness.
 */
    public void create_index() {
        out.println("Creating index for table: " + name);

        // Ensure index is initialized
        index.clear(); // Reset index

        for (Comparable[] tuple : tuples) {
            var keyVal = new Comparable[key.length];
            var cols = match(key);
            for (var j = 0; j < keyVal.length; j++) {
                keyVal[j] = tuple[cols[j]];
            }
            index.put(new KeyType(keyVal), tuple);
        }
    }

    /************************************************************************************
     * Create a unique index for this table based on the primary key.
     * This ensures that there are no duplicate primary keys.
     */
    public void create_unique_index() {
        out.println("Creating unique index for table: " + name);

        // Clear any existing index
        if (index != null) index.clear();
        
        // Rebuild the index while enforcing uniqueness
        for (Comparable[] tuple : tuples) {
            var keyVal = new Comparable[key.length];
            var cols = match(key);
            
            for (var j = 0; j < keyVal.length; j++) {
                keyVal[j] = tuple[cols[j]];
            }

            KeyType keyType = new KeyType(keyVal);

            if (index.containsKey(keyType)) {
                out.println("Error: Duplicate primary key detected: " + keyType);
                continue;  // Skip duplicate keys
            }

            index.put(keyType, tuple);  // Insert into LinHashMap
        }
    }


    


    /************************************************************************************
     * Project the tuples onto a lower dimension by keeping only the given
     * attributes.
     * Check whether the original key is included in the projection.
     *
     * #usage movie.project ("title year studioNo")
     *
     * @param attributes the attributes to project onto
     * @return a table of projected tuples
     */
    public Table project(String attributes) {
        out.println("RA> " + name + ".project (" + attributes + ")");
        var attrs = attributes.split(" ");
        var colDomain = extractDom(match(attrs), domain);
        var newKey = (Arrays.asList(attrs).containsAll(Arrays.asList(key))) ? key : attrs;

        List<Comparable[]> rows = new ArrayList<>();

        // Determine column positions for the attributes to project
        int[] colPositions = match(attrs);

        // Extract the projected columns from each tuple
        for (Comparable[] tuple : tuples) {
            Comparable[] projectedTuple = new Comparable[colPositions.length];
            for (int i = 0; i < colPositions.length; i++) {
                projectedTuple[i] = tuple[colPositions[i]];
            }
            rows.add(projectedTuple);
        }

        return new Table(name + count++, attrs, colDomain, newKey, rows);
    } // project

    /************************************************************************************
     * Select the tuples satisfying the given predicate (Boolean function).
     *
     * #usage movie.select (t -> t[movie.col("year")].equals (1977))
     *
     * @param predicate the check condition for tuples
     * @return a table with tuples satisfying the predicate
     */
    public Table select(Predicate<Comparable[]> predicate) {
        out.println("RA> " + name + ".select (" + predicate + ")");

        return new Table(name + count++, attribute, domain, key,
                tuples.stream().filter(t -> predicate.test(t))
                        .collect(Collectors.toList()));
    } // select

    /************************************************************************************
     * Select the tuples satisfying the given simple condition on
     * attributes/constants
     * compared using an <op> ==, !=, <, <=, >, >=.
     *
     * #usage movie.select ("year == 1977")
     *
     * @param condition the check condition as a string for tuples
     * @return a table with tuples satisfying the condition
     */
    public Table select(String condition) {
        out.println("ENTERED EQUALS" + condition);
        out.println("RA> " + name + ".select (" + condition + ")");

        List<Comparable[]> rows = new ArrayList<>();

        // Split the condition into components
        var token = condition.split(" ");
        if (token.length != 3) {
            out.println("Invalid condition format");
            return null;
        }

        // Get column number and determine numeric type
        var colNo = col(token[0]);
        boolean isNumeric = domain[colNo].equals("Integer") ||
                domain[colNo].equals("Float") ||
                domain[colNo].equals("Double");

        for (var t : tuples) {
            boolean satisfiesCondition = false;

            try {

                switch (token[1]) {
                    case "=":

                        satisfiesCondition = isNumeric
                                ? domain[colNo].equals("Integer")
                                        ? ((Integer) t[colNo]).compareTo(Integer.parseInt(token[2])) == 0
                                        : domain[colNo].equals("Float")
                                                ? ((Float) t[colNo]).compareTo(Float.parseFloat(token[2])) == 0
                                                : ((Double) t[colNo]).compareTo(Double.parseDouble(token[2])) == 0
                                : t[colNo].toString().equals(token[2]);
                        break;
                    case "<":
                        satisfiesCondition = isNumeric
                                ? domain[colNo].equals("Integer")
                                        ? ((Integer) t[colNo]).compareTo(Integer.parseInt(token[2])) < 0
                                        : domain[colNo].equals("Float")
                                                ? ((Float) t[colNo]).compareTo(Float.parseFloat(token[2])) < 0
                                                : ((Double) t[colNo]).compareTo(Double.parseDouble(token[2])) < 0
                                : t[colNo].toString().compareTo(token[2]) < 0;
                        break;
                    case ">":
                        satisfiesCondition = isNumeric
                                ? domain[colNo].equals("Integer")
                                        ? ((Integer) t[colNo]).compareTo(Integer.parseInt(token[2])) > 0
                                        : domain[colNo].equals("Float")
                                                ? ((Float) t[colNo]).compareTo(Float.parseFloat(token[2])) > 0
                                                : ((Double) t[colNo]).compareTo(Double.parseDouble(token[2])) > 0
                                : t[colNo].toString().compareTo(token[2]) > 0;
                        break;
                    case "<=":
                        satisfiesCondition = isNumeric
                                ? domain[colNo].equals("Integer")
                                        ? ((Integer) t[colNo]).compareTo(Integer.parseInt(token[2])) <= 0
                                        : domain[colNo].equals("Float")
                                                ? ((Float) t[colNo]).compareTo(Float.parseFloat(token[2])) <= 0
                                                : ((Double) t[colNo]).compareTo(Double.parseDouble(token[2])) <= 0
                                : t[colNo].toString().compareTo(token[2]) <= 0;
                        break;
                    case ">=":
                        satisfiesCondition = isNumeric
                                ? domain[colNo].equals("Integer")
                                        ? ((Integer) t[colNo]).compareTo(Integer.parseInt(token[2])) >= 0
                                        : domain[colNo].equals("Float")
                                                ? ((Float) t[colNo]).compareTo(Float.parseFloat(token[2])) >= 0
                                                : ((Double) t[colNo]).compareTo(Double.parseDouble(token[2])) >= 0
                                : t[colNo].toString().compareTo(token[2]) >= 0;
                        break;
                    default:
                        out.println("Unknown operator: " + token[1]);
                        return null;
                }
            } catch (Exception e) {
                out.println("Comparison error: " + e.getMessage());
                return null;
            }

            if (satisfiesCondition) {
                rows.add(t);
            }
        }

        return new Table(name + count++, attribute, domain, key, rows);
    }// select

    /************************************************************************************
     * Does tuple t satify the condition t[colNo] op value where op is ==, !=,
     * <, <=, >, >=?
     *
     * #usage satisfies (t, 1, "<", "1980")
     *
     * @param colNo the attribute's column number
     * @param op    the comparison operator
     * @param value the value to compare with (must be converted, String -> domain
     *              type)
     * @return whether the condition is satisfied
     */
    private boolean satifies(Comparable[] t, int colNo, String op, String value) {
        var t_A = t[colNo];
        out.println("satisfies: " + t_A + " " + op + " " + value);
        var valt = switch (domain[colNo].getSimpleName()) { // type converted
            case "Byte" -> Byte.valueOf(value);
            case "Character" -> value.charAt(0);
            case "Double" -> Double.valueOf(value);
            case "Float" -> Float.valueOf(value);
            case "Integer" -> Integer.valueOf(value);
            case "Long" -> Long.valueOf(value);
            case "Short" -> Short.valueOf(value);
            case "String" -> value;
            default -> value;
        }; // switch
        var comp = t_A.compareTo(valt);

        return switch (op) {
            case "==" -> comp == 0;
            case "!=" -> comp != 0;
            case "<" -> comp < 0;
            case "<=" -> comp <= 0;
            case ">" -> comp > 0;
            case ">=" -> comp >= 0;
            default -> false;
        }; // switch
    } // satifies

    /************************************************************************************
     * Select the tuples satisfying the given key predicate (key = value). Use an
     * index
     * (Map) to retrieve the tuple with the given key value. INDEXED SELECT
     * algorithm.
     *
     * @param keyVal the given key value
     * @return a table with the tuple satisfying the key predicate
     */
    public Table select(KeyType keyVal) {
        out.println("RA> " + name + ".select (" + keyVal + ")");

        List<Comparable[]> rows = new ArrayList<>();

        // T O B E I M P L E M E N T E D - Project 2

        return new Table(name + count++, attribute, domain, key, rows);
    } // select

    /************************************************************************************
     * Union this table and table2. Check that the two tables are compatible.
     *
     * #usage movie.union (show)
     *
     * @param table2 the rhs table in the union operation
     * @return a table representing the union
     */
    public Table union(Table table2) {
        out.println("RA> " + name + ".union (" + table2.name + ")");
        if (!compatible(table2))
            return null;

        List<Comparable[]> rows = new ArrayList<>();

        // Add all rows from this table
        rows.addAll(tuples);

        // Add all rows from table2
        rows.addAll(table2.tuples);

        // T O B E I M P L E M E N T E D

        return new Table(name + count++, attribute, domain, key, rows);
    } // union

    /************************************************************************************
     * Take the difference of this table and table2. Check that the two tables are
     * compatible.
     *
     * #usage movie.minus (show)
     *
     * @param table2 The rhs table in the minus operation
     * @return a table representing the difference
     */
    public Table minus(Table table2) {
        out.println("RA> " + name + ".minus (" + table2.name + ")");
        if (!compatible(table2))
            return null;

        List<Comparable[]> rows = new ArrayList<>();

        // Iterate through the tuples of this table
        for (var t : tuples) {
            boolean found = false;

            // Check if the tuple exists in table2
            for (var t2 : table2.tuples) {
                if (Arrays.equals(t, t2)) {
                    found = true;
                    break;
                }
            }

            // Add the tuple to the result if it does not exist in table2
            if (!found)
                rows.add(t);
        }

        return new Table(name + count++, attribute, domain, key, rows);
    } // minus

    /************************************************************************************
     * Join this table and table2 by performing an "equi-join". Tuples from both
     * tables
     * are compared requiring attributes1 to equal attributes2. Disambiguate
     * attribute
     * names by appending "2" to the end of any duplicate attribute name. Implement
     * using
     * a NESTED LOOP JOIN ALGORITHM.
     *
     * #usage movie.join ("studioName", "name", studio)
     *
     * @param attributes1 the attributes of this table to be compared (Foreign Key)
     * @param attributes2 the attributes of table2 to be compared (Primary Key)
     * @param table2      the rhs table in the join operation
     * @return a table with tuples satisfying the equality predicate
     */
    public Table join(String attributes1, String attributes2, Table table2) {
    out.println("RA> " + name + ".join (" + attributes1 + ", " + attributes2 + ", " + table2.name + ")");

    var t_attrs = attributes1.split(" ");
    var u_attrs = attributes2.split(" ");
    var rows = new ArrayList<Comparable[]>();

    // Get column indices for join attributes in both tables
    int[] t_cols = match(t_attrs);
    int[] u_cols = table2.match(u_attrs);

    // Nested loop join
    for (var t : tuples) {
        for (var u : table2.tuples) {
            boolean joinMatch = true;
            
            // Check if join attributes match
            for (int j = 0; j < t_cols.length; j++) {
                if (!t[t_cols[j]].equals(u[u_cols[j]])) {
                    joinMatch = false;
                    break;
                }
            }

            if (joinMatch) {
                rows.add(concat(t, u));
            }
        }
    }

    return new Table(name + count++, 
                    concat(attribute, table2.attribute),
                    concat(domain, table2.domain), 
                    key, 
                    rows);
}
    /************************************************************************************
     * Join this table and table2 by performing a "theta-join". Tuples from both
     * tables
     * are compared attribute1 <op> attribute2. Disambiguate attribute names by
     * appending "2"
     * to the end of any duplicate attribute name. Implement using a Nested Loop
     * Join algorithm.
     *
     * #usage movie.join ("studioName == name", studio)
     *
     * @param condition the theta join condition
     * @param table2    the rhs table in the join operation
     * @return a table with tuples satisfying the condition
     */
    public Table join(String condition, Table table2) {
    out.println("RA> " + name + ".join (" + condition + ", " + table2.name + ")");

    var rows = new ArrayList<Comparable[]>();
    var token = condition.split(" ");
    
    if (token.length != 3) {
        out.println("join ERROR: malformed theta-join condition");
        return null;
    }

    var attr1 = token[0];
    var op = token[1];
    var attr2 = token[2];

    var col1 = col(attr1);
    var col2 = table2.col(attr2);

    // Check if columns exist
    if (col1 == -1 || col2 == -1) {
        out.println("join ERROR: attributes not found");
        return null;
    }

    // Nested loop join with theta condition
    for (var t : tuples) {
        for (var u : table2.tuples) {
            if (satifies(t, col1, op, u[col2].toString())) {
                rows.add(concat(t, u));
            }
        }
    }

    return new Table(name + count++, 
                    concat(attribute, table2.attribute),
                    concat(domain, table2.domain), 
                    key, 
                    rows);
}// join

    /************************************************************************************
     * Join this table and table2 by performing an "equi-join". Same as above
     * equi-join,
     * but implemented using an INDEXED JOIN algorithm.
     *
     * @param attributes1 the attributes of this table to be compared (Foreign Key)
     * @param attributes2 the attributes of table2 to be compared (Primary Key)
     * @param table2      the rhs table in the join operation
     * @return a table with tuples satisfying the equality predicate
     */
    public Table i_join(String attributes1, String attributes2, Table table2) {
    out.println("RA> " + name + ".i_join (" + attributes1 + ", " + attributes2 + ", " + table2.name + ")");

    // Split the attributes into arrays
    var t_attrs = attributes1.split(" ");
    var u_attrs = attributes2.split(" ");

    // Get column positions for join attributes in both tables
    int[] t_cols = match(t_attrs);
    int[] u_cols = table2.match(u_attrs);

    // Create a hash index for table2 (the build phase)
    Map<String, List<Comparable[]>> hashIndex = new HashMap<>();
    
    // Build phase - create hash index on table2
    for (Comparable[] tuple2 : table2.tuples) {
        // Create composite key from join attributes
        StringBuilder keyBuilder = new StringBuilder();
        for (int col : u_cols) {
            if (tuple2[col] != null) {  // Handle null values
                keyBuilder.append(tuple2[col]).append(":");
            }
        }
        String joinKey = keyBuilder.toString();
        
        // Add tuple to hash index
        hashIndex.computeIfAbsent(joinKey, k -> new ArrayList<>()).add(tuple2);
    }

    // Probe phase - use the index to find matches
    List<Comparable[]> results = new ArrayList<>();
    
    // For each tuple in the first table
    for (Comparable[] tuple1 : tuples) {
        // Create the join key
        StringBuilder keyBuilder = new StringBuilder();
        for (int col : t_cols) {
            if (tuple1[col] != null) {  // Handle null values
                keyBuilder.append(tuple1[col]).append(":");
            }
        }
        String joinKey = keyBuilder.toString();
        
        // Look up matching tuples in the hash index
        List<Comparable[]> matches = hashIndex.get(joinKey);
        
        if (matches != null) {
            // For each matching tuple, create a joined tuple
            for (Comparable[] tuple2 : matches) {
                results.add(concat(tuple1, tuple2));
            }
        }
    }

    // Return the resulting table
    return new Table(name + count++, 
                    concat(attribute, table2.attribute),
                    concat(domain, table2.domain), 
                    key, 
                    results);
} // i_join

    /************************************************************************************
     * Join this table and table2 by performing an NATURAL JOIN. Tuples from both
     * tables
     * are compared requiring common attributes to be equal. The duplicate column is
     * also
     * eliminated.
     *
     * #usage movieStar.join (starsIn)
     *
     * @param table2 the rhs table in the join operation
     * @return a table with tuples satisfying the equality predicate
     */
    public Table join(Table table2) {
    out.println("RA> " + name + ".join (" + table2.name + ")");

    // Find common attributes
    List<String> commonAttrs = new ArrayList<>();
    List<Integer> t_cols = new ArrayList<>();
    List<Integer> u_cols = new ArrayList<>();
    
    for (int i = 0; i < attribute.length; i++) {
        for (int j = 0; j < table2.attribute.length; j++) {
            if (attribute[i].equals(table2.attribute[j])) {
                commonAttrs.add(attribute[i]);
                t_cols.add(i);
                u_cols.add(j);
            }
        }
    }

    // Create new attribute and domain arrays (without duplicates)
    List<String> newAttrs = new ArrayList<>(Arrays.asList(attribute));
    List<Class> newDoms = new ArrayList<>(Arrays.asList(domain));
    
    for (int i = 0; i < table2.attribute.length; i++) {
        if (!commonAttrs.contains(table2.attribute[i])) {
            newAttrs.add(table2.attribute[i]);
            newDoms.add(table2.domain[i]);
        }
    }

    var rows = new ArrayList<Comparable[]>();

    // Perform natural join
    for (var t : tuples) {
        for (var u : table2.tuples) {
            boolean matchAll = true;
            
            // Check if all common attributes match
            for (int i = 0; i < commonAttrs.size(); i++) {
                if (!t[t_cols.get(i)].equals(u[u_cols.get(i)])) {
                    matchAll = false;
                    break;
                }
            }

            if (matchAll) {
                // Create new tuple with correct number of attributes
                Comparable[] newTuple = new Comparable[newAttrs.size()];
                
                // Copy attributes from first tuple
                System.arraycopy(t, 0, newTuple, 0, t.length);
                
                // Copy non-common attributes from second tuple
                int pos = t.length;
                for (int i = 0; i < table2.attribute.length; i++) {
                    if (!commonAttrs.contains(table2.attribute[i])) {
                        newTuple[pos++] = u[i];
                    }
                }
                
                rows.add(newTuple);
            }
        }
    }

    return new Table(name + count++,
                    newAttrs.toArray(new String[0]),
                    newDoms.toArray(new Class[0]),
                    key,
                    rows);
} // join

    /************************************************************************************
     * Return the column position for the given attribute name or -1 if not found.
     *
     * @param attr the given attribute name
     * @return a column position
     */
    public int col(String attr) {
        for (var i = 0; i < attribute.length; i++) {
            if (attr.equals(attribute[i]))
                return i;
        } // for

        return -1; // -1 => not found
    } // col

    /************************************************************************************
     * Insert a tuple to the table.
     *
     * #usage movie.insert ("Star_Wars", 1977, 124, "T", "Fox", 12345)
     *
     * @param tup the array of attribute values forming the tuple
     * @return the insertion position/index when successful, else -1
     */
    public int insert(Comparable[] tup) {
        out.println("DML> insert into " + name + " values (" + Arrays.toString(tup) + ")");

        if (!typeCheck(tup)) return -1;  // Validate data types

        var keyVal = new Comparable[key.length];
        var cols = match(key);
        
        for (var j = 0; j < keyVal.length; j++) {
            keyVal[j] = tup[cols[j]];
        }

        KeyType keyType = new KeyType(keyVal);

        // If an index exists, use it to enforce uniqueness
        if (index != null && index.containsKey(keyType)) {
            out.println("Insert Error: Duplicate key " + keyType);
            return -1;  // Reject duplicate insertion
        }

        tuples.add(tup);
        
        if (index != null) index.put(keyType, tup);  // Add to LinHashMap index
        
        return tuples.size() - 1;  // Return insertion position
    } // insert

    /************************************************************************************
     * Get the tuple at index position i.
     *
     * @param i the index of the tuple being sought
     * @return the tuple at index position i
     */
    public Comparable[] get(int i) {
        return tuples.get(i);
    } // get

    /************************************************************************************
     * Get the name of the table.
     *
     * @return the table's name
     */
    public String getName() {
        return name;
    } // getName

    /************************************************************************************
     * Print tuple tup.
     * 
     * @param tup the array of attribute values forming the tuple
     */
    public void printTup(Comparable[] tup) {
        out.print("| ");
        for (var attr : tup)
            out.printf("%15s", attr);
        out.println(" |");
    } // printTup

    /************************************************************************************
     * Print this table.
     */
    public void print() {
        out.println("\n Table " + name);
        out.print("|-");
        out.print("---------------".repeat(attribute.length));
        out.println("-|");
        out.print("| ");
        for (var a : attribute)
            out.printf("%15s", a);
        out.println(" |");
        out.print("|-");
        out.print("---------------".repeat(attribute.length));
        out.println("-|");
        for (var tup : tuples)
            printTup(tup);
        out.print("|-");
        out.print("---------------".repeat(attribute.length));
        out.println("-|");
    } // print

    /************************************************************************************
     * Print this table's index (Map).
     */
    public void printIndex() {
        out.println("\n Index for " + name);
        out.println("-------------------");
        if (mType != MapType.NO_MAP) {
            for (var e : index.entrySet()) {
                out.println(e.getKey() + " -> " + Arrays.toString(e.getValue()));
            } // for
        } // if
        out.println("-------------------");
    } // printIndex

    /************************************************************************************
     * Load the table with the given name into memory.
     *
     * @param name the name of the table to load
     */
    public static Table load(String name) {
        Table tab = null;
        try {
            ObjectInputStream ois = new ObjectInputStream(new FileInputStream(DIR + name + EXT));
            tab = (Table) ois.readObject();
            ois.close();
        } catch (IOException ex) {
            out.println("load: IO Exception");
            ex.printStackTrace();
        } catch (ClassNotFoundException ex) {
            out.println("load: Class Not Found Exception");
            ex.printStackTrace();
        } // try
        return tab;
    } // load

    /************************************************************************************
     * Save this table in a file.
     */
    public void save() {
        try {
            var oos = new ObjectOutputStream(new FileOutputStream(DIR + name + EXT));
            oos.writeObject(this);
            oos.close();
        } catch (IOException ex) {
            out.println("save: IO Exception");
            ex.printStackTrace();
        } // try
    } // save

    // ----------------------------------------------------------------------------------
    // Private Methods
    // ----------------------------------------------------------------------------------

    /************************************************************************************
     * Determine whether the two tables (this and table2) are compatible, i.e., have
     * the same number of attributes each with the same corresponding domain.
     *
     * @param table2 the rhs table
     * @return whether the two tables are compatible
     */
    private boolean compatible(Table table2) {
        if (domain.length != table2.domain.length) {
            out.println("compatible ERROR: table have different arity");
            return false;
        } // if
        for (var j = 0; j < domain.length; j++) {
            if (domain[j] != table2.domain[j]) {
                out.println("compatible ERROR: tables disagree on domain " + j);
                return false;
            } // if
        } // for
        return true;
    } // compatible

    /************************************************************************************
     * Match the column and attribute names to determine the domains.
     *
     * @param column the array of column names
     * @return an array of column index positions
     */
    private int[] match(String[] column) {
        int[] colPos = new int[column.length];

        for (var j = 0; j < column.length; j++) {
            var matched = false;
            for (var k = 0; k < attribute.length; k++) {
                if (column[j].equals(attribute[k])) {
                    matched = true;
                    colPos[j] = k;
                } // for
            } // for
            if (!matched)
                out.println("match: domain not found for " + column[j]);
        } // for

        return colPos;
    } // match

    /************************************************************************************
     * Extract the attributes specified by the column array from tuple t.
     *
     * @param t      the tuple to extract from
     * @param column the array of column names
     * @return a smaller tuple extracted from tuple t
     */
    private Comparable[] extract(Comparable[] t, String[] column) {
        var tup = new Comparable[column.length];
        var colPos = match(column);
        for (var j = 0; j < column.length; j++)
            tup[j] = t[colPos[j]];
        return tup;
    } // extract

    /************************************************************************************
     * Check the size of the tuple (number of elements in array) as well as the type
     * of
     * each value to ensure it is from the right domain.
     *
     * @param t the tuple as a array of attribute values
     * @return whether the tuple has the right size and values that comply
     *         with the given domains
     */
    private boolean typeCheck(Comparable[] t) {
        // T O B E I M P L E M E N T E D

        return true; // change once implemented
    } // typeCheck

    /************************************************************************************
     * Find the classes in the "java.lang" package with given names.
     *
     * @param className the array of class name (e.g., {"Integer", "String"})
     * @return an array of Java classes
     */
    private static Class[] findClass(String[] className) {
        var classArray = new Class[className.length];

        for (var i = 0; i < className.length; i++) {
            try {
                classArray[i] = Class.forName("java.lang." + className[i]);
            } catch (ClassNotFoundException ex) {
                out.println("findClass: " + ex);
            } // try
        } // for

        return classArray;
    } // findClass

    /************************************************************************************
     * Extract the corresponding domains.
     *
     * @param colPos the column positions to extract.
     * @param group  where to extract from
     * @return the extracted domains
     */
    private Class[] extractDom(int[] colPos, Class[] group) {
        var obj = new Class[colPos.length];

        for (var j = 0; j < colPos.length; j++) {
            obj[j] = group[colPos[j]];
        } // for

        return obj;
    } // extractDom

} // Table
