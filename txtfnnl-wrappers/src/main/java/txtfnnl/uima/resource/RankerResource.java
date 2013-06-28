package txtfnnl.uima.resource;

import ciir.umass.edu.learning.DataPoint;
import ciir.umass.edu.learning.RankList;
import org.apache.uima.resource.SharedResourceObject;

import java.util.List;

/** Currently, only supports methods needed to rank a list given a trained model. */
public
interface RankerResource extends SharedResourceObject {
  public
  RankList rank(RankList rl);

  public
  double eval(DataPoint db);

  public
  List<RankList> rank(List<RankList> rll);
}
