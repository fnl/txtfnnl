package txtfnnl.uima.resource;

import ciir.umass.edu.learning.RankList;
import org.apache.uima.resource.SharedResourceObject;
import ciir.umass.edu.learning.Ranker;

import java.util.List;

/**
 * Currently, only supports methods needed to rank a list given a trained model.
 */
public
interface RankerResource extends SharedResourceObject {
  public Ranker getRanker();
  public RankList rank(RankList rl);
  public List<RankList> rank(List<RankList> rll);
}
