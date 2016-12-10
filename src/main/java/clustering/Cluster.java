package clustering;

import java.util.ArrayList;
import java.util.UUID;

/**
 *
 * @author ggianna
 */
public class Cluster extends ArrayList<Sequence> implements Comparable<Cluster> {
    String sID;
    public Cluster() {
        sID = UUID.randomUUID().toString();
    }

    public String getID() {
        return sID;
    }

    @Override
    public int compareTo(Cluster t) {
        return sID.compareTo(t.getID());
    }

    @Override
    public int hashCode() {
        return sID.hashCode();
    }


    @Override
    public String toString() {
        return sID + " : " + super.toString();
    }
}
