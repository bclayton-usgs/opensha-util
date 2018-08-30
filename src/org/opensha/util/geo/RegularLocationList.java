package gov.usgs.earthquake.nshmp.geo;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.List;

import com.google.common.collect.ForwardingList;
import com.google.common.collect.ImmutableList;

/**
 * Default LocationList implementation backed by an ImmutableList.
 * 
 * @author Peter Powers
 */
final class RegularLocationList extends ForwardingList<Location> implements LocationList2 {

  final ImmutableList<Location> locs;

  RegularLocationList(ImmutableList<Location> locs) {
    checkArgument(locs.size() > 0, "LocationList is empty");
    this.locs = locs;
  }

  @Override
  protected List<Location> delegate() {
    return locs;
  }
  
  @Override
  public LocationList2 reverse() {
    return new RegularLocationList(locs.reverse());
  }
  
}
