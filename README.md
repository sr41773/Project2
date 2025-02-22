# Project 2
Group Members: 
Isra Naweed 
Keerthana Ramesh
Shriya Rasale
Tharushika Dehipitiarachchi
Anjali Devarapalli

### Contributions
Tharushika Contribution: Implemented Set and Split function in LinHashMap. Implemented Insert. 
Added unit tests for duplicate indexes. Made create_index and create_unique_index
Isra Naweed Contribution: 
Keerthana Ramesh Contribution: 
Shriya Rasale Contribution: Select Relation
Anjali Devarapalli Contribution: 


In the union method, "key" is the primary key. However, this is not very useful as there might be cases where "key" is not unique for each row.

We did not implement the Harrison_Ford test case which corresponded to the below method which was to be 
implemented in Project 2.

public Table select(KeyType keyVal) {
        out.println("RA> " + name + ".select (" + keyVal + ")");

        List<Comparable[]> rows = new ArrayList<>();

        // T O B E I M P L E M E N T E D - Project 2

        return new Table(name + count++, attribute, domain, key, rows);
    } // select


### Important Run Instructions
For LinHashMap to compile: javac --enable-preview --release 23 LinHashMap.
To run: java --enable-preview LinHashMap


