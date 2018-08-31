package org.opensha.util.geo;

import static org.opensha.util.Text.NEWLINE;
import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.acos;
import static java.lang.Math.asin;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Math.sin;
import static java.lang.Math.sqrt;
import static org.opensha.util.geo.Coordinates.EARTH_RADIUS_MEAN;
import static org.opensha.util.geo.Coordinates.LAT_RANGE;
import static org.opensha.util.geo.Coordinates.LON_RANGE;

import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import org.opensha.util.Maths;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.math.DoubleMath;

/**
 * Static utility methods to operate on geographic {@code Location} data.
 *
 * <p> See: <a href="http://williams.best.vwh.net/avform.htm" target="_blank">
 * Aviation Formulary</a> for formulae implemented in this class as well as <a
 * href="http://www.movable-type.co.uk/scripts/latlong.html"
 * target="_blank">Moveable Type Scripts</a> for other implementations.
 *
 * @author Peter Powers
 * @see Location
 */
public final class Locations {

  /*
   * TODO It's good to have these algorithms all in one file. However, it might
   * be nice to add methods such as translate(vector) or distanceTo(loc) to
   * Location, which would allow more elegant method chaining.
   */

  /*
   * Developer Notes: All experimental, exploratory and test methods were moved
   * to the LocationUtilsTest.java. On the basis of various experiments, older
   * methods to calculate distance were replaced with updated versions, many of
   * which leverage spherical geometry to yield more accurate results. Some
   * 'fast' versions were updated as well. All legacy methods, however, are
   * preserved in LocationUtilsTest.java where comparison tests can be rerun.
   * P.Powers 3-6-2010
   *
   * Most methods take Locations exclusively as arguments. This alleviates any
   * error checking that must otherwise be performed on user supplied lat-lon
   * values. It also alleviates the need for expensive degree-radian conversions
   * by using radians, the native format for Locations, exclusively.
   *
   * TODO: Add log warnings when 'fast' methods are being used for points that
   * exceed some max separation.
   */

  /* No instantiation allowed */
  private Locations() {}

  /**
   * Calculates the angle between two {@code Location}s using the <a
   * href="http://en.wikipedia.org/wiki/Haversine_formula" target="_blank">
   * Haversine</a> formula. This method properly handles values spanning ±180°.
   * See <a href="http://williams.best.vwh.net/avform.htm#Dist"> Aviation
   * Formulary</a> for source. Result is returned in radians.
   *
   * @param p1 the first {@code Location} point
   * @param p2 the second {@code Location} point
   * @return the angle between the points (in radians)
   */
  public static double angle(Location p1, Location p2) {
    double sinDlatBy2 = sin((p2.latRad - p1.latRad) / 2.0);
    double sinDlonBy2 = sin((p2.lonRad - p1.lonRad) / 2.0);
    // half length of chord connecting points
    double c = (sinDlatBy2 * sinDlatBy2) +
        (cos(p1.latRad) * cos(p2.latRad) * sinDlonBy2 * sinDlonBy2);
    return 2.0 * atan2(sqrt(c), sqrt(1 - c));
  }

  /**
   * Calculates the great circle surface distance between two {@code Location} s
   * using the Haversine formula for computing the angle between two points. For
   * a faster, but less accurate implementation at large separations, see
   * {@link #horzDistanceFast(Location, Location)}.
   *
   * @param p1 the first {@code Location} point
   * @param p2 the second {@code Location} point
   * @return the distance between the points in km
   * @see #angle(Location, Location)
   * @see #horzDistanceFast(Location, Location)
   */
  public static double horzDistance(Location p1, Location p2) {
    return EARTH_RADIUS_MEAN * angle(p1, p2);
  }

  /**
   * Calculates approximate distance between two {@code Location}s. This method
   * is about 2 orders of magnitude faster than {@code horzDistance()}, but is
   * imprecise at large distances. Method uses the latitudinal and longitudinal
   * differences between the points as the sides of a right triangle. The
   * longitudinal distance is scaled by the cosine of the mean latitude.
   *
   * <p><b>Note:</b> This method does <i>NOT</i> support values spanning
   * #177;180° and fails where the numeric angle exceeds 180°. Convert data to
   * the 0-360° interval or use {@link #horzDistance(Location, Location)} in
   * such instances.
   *
   * @param p1 the first {@code Location} point
   * @param p2 the second {@code Location} point
   * @return the distance between the points in km
   * @see #horzDistance(Location, Location)
   */
  public static double horzDistanceFast(Location p1, Location p2) {
    // modified from J. Zechar:
    // calculates distance between two points, using formula
    // as specifed by P. Shebalin via email 5.8.2004
    double dLat = p1.latRad - p2.latRad;
    double dLon = (p1.lonRad - p2.lonRad) * cos((p1.latRad + p2.latRad) * 0.5);
    return EARTH_RADIUS_MEAN * sqrt(dLat * dLat + dLon * dLon);
  }

  /**
   * Returns the vertical separation between two {@code Location}s. The returned
   * value is not absolute and preserves the sign of the difference between the
   * points.
   *
   * @param p1 the first {@code Location} point
   * @param p2 the first {@code Location} point
   * @return the vertical separation between the points
   */
  public static double vertDistance(Location p1, Location p2) {
    return p2.depth - p1.depth;
  }

  /**
   * Calculates the distance in three dimensions between two {@code Location}s
   * using spherical geometry. Method returns the straight line distance taking
   * into account the depths of the points. For a faster, but less accurate
   * implementation at large separations, see
   * {@link #linearDistanceFast(Location, Location)}.
   *
   * @param p1 the first {@code Location} point
   * @param p2 the second {@code Location} point
   * @return the distance in km between the points
   * @see #linearDistanceFast(Location, Location)
   */
  public static double linearDistance(Location p1, Location p2) {
    double alpha = angle(p1, p2);
    double R1 = EARTH_RADIUS_MEAN - p1.depth;
    double R2 = EARTH_RADIUS_MEAN - p2.depth;
    double B = R1 * sin(alpha);
    double C = R2 - R1 * cos(alpha);
    return sqrt(B * B + C * C);
  }

  /**
   * Calculates the approximate linear distance in three dimensions between two
   * {@code Location}s. This simple and speedy implementation uses the
   * Pythagorean theorem, treating horizontal and vertical separations as
   * orthogonal.
   *
   * <p><b>Note:</b> This method is very imprecise at large separations and
   * should not be used for points >200km apart. If an estimate of separation
   * distance is not known in advance use
   * {@link #linearDistance(Location, Location)} for more reliable results.
   *
   * <p><b>Note:</b> This method fails for values spanning ±180°; see
   * {@link #horzDistanceFast(Location, Location)}.
   *
   * @param p1 the first {@code Location} point
   * @param p2 the second {@code Location} point
   * @return the distance in km between the points
   * @see #linearDistance(Location, Location)
   */
  // TODO examine whether all uses of this method are appropriate or
  // if more accurate linearDistance() should be used instead
  public static double linearDistanceFast(Location p1, Location p2) {
    double h = horzDistanceFast(p1, p2);
    double v = vertDistance(p1, p2);
    return sqrt(h * h + v * v);
  }

  /**
   * Computes the shortest distance between a point and a line (great-circle).
   * that extends infinitely in both directions. Both the line and point are
   * assumed to be at the earth's surface; the depth component of each
   * {@code Location} is ignored. This method uses the true spherical geometric
   * function for 'off-track distance'; See <a
   * href="http://williams.best.vwh.net/avform.htm#XTE"> Aviation Formulary</a>
   * for source. The sign of the result indicates which side of the supplied
   * line {@code p3} is on (right:[+] left:[-]).
   *
   * <p>This method, though more accurate over longer distances and line
   * lengths, is up to 20x slower than
   * {@link #distanceToLineFast(Location, Location, Location)}. However, this
   * method returns accurate results for values spanning #177;180°.
   *
   * <p>If the line should instead be treated as a segment such that the result
   * will be a distance to an endpoint if {@code p3} does not project onto the
   * segment, use {@link #distanceToSegment(Location, Location, Location)}
   * instead.
   *
   * @param p1 the first {@code Location} point on the line
   * @param p2 the second {@code Location} point on the line
   * @param p3 the {@code Location} point for which distance will be calculated
   * @return the shortest distance in km between the supplied point and line
   * @see #distanceToLineFast(Location, Location, Location)
   * @see #distanceToSegment(Location, Location, Location)
   */
  public static double distanceToLine(Location p1, Location p2, Location p3) {
    // angular distance
    double ad13 = angle(p1, p3);
    // delta azimuth p1 to p3 and azimuth p1 to p2
    double Daz13az12 = azimuthRad(p1, p3) - azimuthRad(p1, p2);
    // cross-track distance (in radians)
    double xtd = asin(sin(ad13) * sin(Daz13az12));
    return (abs(xtd) < TOLERANCE) ? 0.0 : xtd * EARTH_RADIUS_MEAN;
  }

  /**
   * Computes the shortest distance between a point and a line. Both the line
   * and point are assumed to be at the earth's surface; the depth component of
   * each {@code Location} is ignored. This is a fast, geometric, cartesion
   * (flat-earth approximation) solution in which longitude of the line points
   * are scaled by the cosine of latitude; it is only appropriate for use over
   * short distances (e.g. <200 km). The sign of the result indicates which side
   * of the supplied line {@code p3} is on (right:[+] left:[-]).
   *
   * <p><b>Note:</b> This method does <i>NOT</i> support values spanning ±180°
   * and results for such input values are not guaranteed. Convert data to the
   * 0-360° interval or use
   * {@link #distanceToLine(Location, Location, Location)} in such instances.
   *
   * <p>If the line should instead be treated as a segment such that the result
   * will be a distance to an endpoint if {@code p3} does not project onto the
   * segment, use {@link #distanceToSegmentFast(Location, Location, Location)}
   * instead.
   *
   * @param p1 the first {@code Location} point on the line
   * @param p2 the second {@code Location} point on the line
   * @param p3 the {@code Location} point for which distance will be calculated
   * @return the shortest distance in km between the supplied point and line
   * @see #distanceToLine(Location, Location, Location)
   * @see #distanceToSegmentFast(Location, Location, Location)
   */
  public static double distanceToLineFast(Location p1, Location p2, Location p3) {
    // use average latitude to scale longitude
    double lonScale = cos(0.5 * p3.latRad + 0.25 * p1.latRad + 0.25 * p2.latRad);
    // first point on line transformed to origin; others scaled by lon
    double x2 = (p2.lonRad - p1.lonRad) * lonScale;
    double y2 = p2.latRad - p1.latRad;
    double x3 = (p3.lonRad - p1.lonRad) * lonScale;
    double y3 = p3.latRad - p1.latRad;
    return (x3 * y2 - x2 * y3) / sqrt(x2 * x2 + y2 * y2) * EARTH_RADIUS_MEAN;
  }

  // TODO reenable once rupture surface code migrated (?? PP 9/17 clean??)
  /**
   * Calculates the distance between the supplied {@code Location} and the
   * {@code EventlyGridddedSurface} by looping over all the locations in the
   * surface and returning the smallest one determined by
   * {@link #horzDistance(Location, Location)}.
   *
   * @param loc a {@code Location}
   * @param rupSurf an EvenlyGriddedSurfaceAPI
   * @return the minimum distance to a surface from the supplied
   *         {@code Location}
   */
  // public static double distanceToSurf(Location loc,
  // AbstractGriddedSurface rupSurf) {
  // double minDistance = Double.MAX_VALUE;
  // double horzDist, vertDist, totalDist;
  // for (Location loc2 : rupSurf) {
  // horzDist = horzDistance(loc, loc2);
  // vertDist = vertDistance(loc, loc2);
  // totalDist = horzDist * horzDist + vertDist * vertDist;
  // if (totalDist < minDistance) minDistance = totalDist;
  // }
  // return pow(minDistance, 0.5);
  // }

  /**
   * Calculates the distance between the supplied {@code Location} and the
   * {@code EventlyGridddedSurface} by looping over all the locations in the
   * surface and returning the smallest one determined by
   * {@link #horzDistanceFast(Location, Location)}.
   *
   * @param loc a {@code Location}
   * @param rupSurf an EvenlyGriddedSurfaceAPI
   * @return the minimum distance to a surface from the supplied
   *         {@code Location}
   */
  // public static double distanceToSurfFast(Location loc, RuptureSurface
  // rupSurf) {
  // double minDistance = Double.MAX_VALUE;
  // double horzDist, vertDist, totalDist;
  //
  // for (Location loc2 : rupSurf.getEvenlyDiscritizedListOfLocsOnSurface()) {
  // horzDist = horzDistanceFast(loc, loc2);
  // vertDist = vertDistance(loc, loc2);
  // totalDist = horzDist * horzDist + vertDist * vertDist;
  // if (totalDist < minDistance) minDistance = totalDist;
  // }
  // return pow(minDistance, 0.5);
  // }

  /**
   * Computes the shortest distance between a point and a line segment (i.e.
   * great-circle segment). Both the line and point are assumed to be at the
   * earth's surface; the depth component of each {@code Location} is ignored.
   * This method uses the true spherical geometric function for 'off-track
   * distance'; See <a href="http://williams.best.vwh.net/avform.htm#XTE">
   * Aviation Formulary</a> for source. This method always returns a positive
   * result.
   *
   * <p>This method, though more accurate over longer distances and line
   * lengths, is up to 20x slower than
   * {@link #distanceToSegmentFast(Location, Location, Location)}. However, this
   * method returns accurate results for values spanning ±180°.
   *
   * <p> If the line should instead be treated as infinite, use
   * {@link #distanceToLine(Location, Location, Location)} instead.
   *
   * @param p1 the first {@code Location} point on the line
   * @param p2 the second {@code Location} point on the line
   * @param p3 the {@code Location} point for which distance will be calculated
   * @return the shortest distance in km between the supplied point and line
   * @see #distanceToLineFast(Location, Location, Location)
   * @see #distanceToLine(Location, Location, Location)
   */
  public static double distanceToSegment(Location p1, Location p2, Location p3) {

    // repeat calcs in distanceToLine() to cut down on replication of
    // expensive trig ops that would result from calling distanceToLine()

    // angular distance
    double ad13 = angle(p1, p3);
    // delta azimuth p1 to p3 and azimuth p1 to p2
    double Daz13az12 = azimuthRad(p1, p3) - azimuthRad(p1, p2);
    // cross-track distance (in radians)
    double xtd = asin(sin(ad13) * sin(Daz13az12));
    // along-track distance (in km)
    double atd = acos(cos(ad13) / cos(xtd)) * EARTH_RADIUS_MEAN;
    // check if beyond p3
    if (atd > horzDistance(p1, p2)) {
      return horzDistance(p2, p3);
    }
    // check if before p1
    if (cos(Daz13az12) < 0) {
      return horzDistance(p1, p3);
    }
    return (abs(xtd) < TOLERANCE) ? 0.0 : abs(xtd) * EARTH_RADIUS_MEAN;
  }

  /**
   * Computes the shortest distance between a point and a line segment. Both the
   * line and point are assumed to be at the earth's surface; the depth
   * component of each {@code Location} is ignored. This is a fast, geometric,
   * cartesion (flat-earth approximation) solution in which longitude of the
   * line points are scaled by the cosine of latitude; it is only appropriate
   * for use over short distances (e.g. <200 km).
   *
   * <p><b>Note:</b> This method fails for values spanning ±180°; see
   * {@link #distanceToSegment(Location, Location, Location)}.
   *
   * <p>If the line should instead be treated as infinite, use
   * {@link #distanceToLineFast(Location, Location, Location)} instead.
   *
   * @param p1 the first {@code Location} point on the line
   * @param p2 the second {@code Location} point on the line
   * @param p3 the {@code Location} point for which distance will be calculated
   * @return the shortest distance in km between the supplied point and line
   * @see #distanceToSegment(Location, Location, Location)
   * @see #distanceToLineFast(Location, Location, Location)
   */
  public static double distanceToSegmentFast(Location p1, Location p2, Location p3) {
    // use average latitude to scale longitude
    double lonScale = cos(0.5 * p3.latRad + 0.25 * p1.latRad + 0.25 * p2.latRad);
    // first point on line transformed to origin; others scaled by lon
    double x2 = (p2.lonRad - p1.lonRad) * lonScale;
    double y2 = p2.latRad - p1.latRad;
    double x3 = (p3.lonRad - p1.lonRad) * lonScale;
    double y3 = p3.latRad - p1.latRad;
    return Line2D.ptSegDist(0, 0, x2, y2, x3, y3) * EARTH_RADIUS_MEAN;
  }

  /**
   * Computes the initial azimuth (bearing) when moving from one
   * {@code Location} to another. See <a
   * href="http://williams.best.vwh.net/avform.htm#Crs"> Aviation Formulary</a>
   * for source. For back azimuth, reverse the {@code Location} arguments.
   * Result is returned in radians over the interval [0, 2π).
   *
   * <p><b>Note:</b> It is more efficient to use this method for computation
   * because {@code Location}s store lat and lon in radians internally. Use
   * {@link #azimuth(Location, Location)} for presentation.
   *
   * @param p1 the first {@code Location} point
   * @param p2 the second {@code Location} point
   * @return the azimuth (bearing) from p1 to p2 in radians
   * @see #azimuth(Location, Location)
   */
  public static double azimuthRad(Location p1, Location p2) {
    // check the poles using a small number ~ machine precision
    if (isPole(p1)) {
      return ((p1.latRad > 0) ? PI : 0); // N : S pole
    }
    // for starting points other than the poles:
    double dLon = p2.lonRad - p1.lonRad;
    double cosLat2 = cos(p2.latRad);
    double azRad = atan2(
        sin(dLon) * cosLat2,
        cos(p1.latRad) * sin(p2.latRad) - sin(p1.latRad) * cosLat2 * cos(dLon));
    return (azRad + Maths.TWO_PI) % Maths.TWO_PI;
  }

  /**
   * Computes the initial azimuth (bearing) when moving from one
   * {@link Location} to another in degrees. See <a
   * href="http://williams.best.vwh.net/avform.htm#Crs"> Aviation Formulary</a>
   * for source. For back azimuth, reverse the {@code Location} arguments.
   * Result is returned in decimal degrees over the interval 0° to 360°.
   *
   * @param p1 the first {@code Location} point
   * @param p2 the second {@code Location} point
   * @return the azimuth (bearing) from p1 to p2 in decimal degrees
   * @see #azimuthRad(Location, Location)
   */
  public static double azimuth(Location p1, Location p2) {
    return azimuthRad(p1, p2) * Maths.TO_DEGREES;
  }

  /**
   * Computes a {@code Location} given an origin point, bearing, and distance.
   * See <a href="http://williams.best.vwh.net/avform.htm#LL"> Aviation
   * Formulary</a> for source. Note that {@code azimuth} is expected in
   * <i>radians</i>.
   *
   * @param p starting location point
   * @param azimuth (bearing) in <i>radians</i> away from origin
   * @param distance (horizontal) along bearing in km
   * @return the end location
   */
  public static Location location(Location p, double azimuth, double distance) {
    return location(p.lonRad, p.latRad, p.depth, azimuth, distance, 0);
  }

  /**
   * Computes a {@code Location} given an origin point and a
   * {@code LocationVector}. See <a
   * href="http://williams.best.vwh.net/avform.htm#LL"> Aviation Formulary</a>
   * for source.
   *
   * @param p starting location point
   * @param d distance along bearing
   * @return the end location
   */
  public static Location location(Location p, LocationVector d) {
    return location(p.lonRad, p.latRad, p.depth, d.azimuth, d.Δh, d.Δv);
  }

  /* Helper assumes radians and kilometers. */
  private static Location location(
      double lon,
      double lat,
      double depth,
      double az,
      double Δh,
      double Δv) {

    double sinLat1 = sin(lat);
    double cosLat1 = cos(lat);
    double ad = Δh / EARTH_RADIUS_MEAN; // angular distance
    double sinD = sin(ad);
    double cosD = cos(ad);
    double lat2 = asin(sinLat1 * cosD + cosLat1 * sinD * cos(az));
    double lon2 = lon + atan2(sin(az) * sinD * cosLat1, cosD - sinLat1 * sin(lat2));
    return Location.create(lat2 * Maths.TO_DEGREES, lon2 * Maths.TO_DEGREES, depth + Δv);
  }

  /**
   * Returns the angle (in decimal degrees) of a line between the first and
   * second location relative to horizontal. This method is intended for use at
   * relatively short separations ( e.g. ≤200km) as it degrades at large
   * distances where curvature is not considered. Note that positive angles are
   * down, negative angles are up.
   * 
   * @param p1 the first {@code Location} point
   * @param p2 the second {@code Location} point
   * @return the plunge of this vector
   */
  public static double plunge(Location p1, Location p2) {
    return LocationVector.create(p1, p2).plunge();
  }

  /**
   * Method returns a unit {@code LocationVector} that bisects the angle defined
   * by the line segments <span style="text-decoration: overline"> {@code p2p1}
   * </span> and <span style="text-decoration: overline"> {@code p2p3}</span>.
   * 
   * @param p1 the first point
   * @param p2 the second point
   * @param p3 the third point
   * @return the bisecting {@code LocationVector}
   */
  public static LocationVector bisect(Location p1, Location p2, Location p3) {
    LocationVector v1 = LocationVector.create(p2, p1);
    LocationVector v2 = LocationVector.create(p2, p3);
    double az = (v2.azimuth + v1.azimuth) / 2.0;
    return LocationVector.create(az, 1, 0);
  }

  /**
   * Tolerance used for location comparisons; 0.000000000001 which in
   * decimal-degrees, radians, and km is comparable to micron-scale precision.
   */
  public static final double TOLERANCE = 0.000000000001;

  /**
   * Returns whether the supplied {@code Location} coincides with one of the
   * poles. Any supplied {@code Location}s that are very close (less than a mm)
   * will return {@code true}.
   *
   * @param p {@code Location} to check
   * @return {@code true} if {@code loc} coincides with one of the earth's
   *         poles, {@code false} otherwise.
   */
  public static boolean isPole(Location p) {
    return cos(p.latRad) < TOLERANCE;
  }

  /**
   * Returns {@code true} if the supplied {@code Location}s are very, very close
   * to one another. Internally, lat, lon, and depth values must be within <1mm
   * of each other.
   *
   * @param p1 the first {@code Location} to compare
   * @param p2 the second {@code Location} to compare
   * @return {@code true} if the supplied {@code Location}s are very close,
   *         {@code false} otherwise.
   */
  public static boolean areSimilar(Location p1, Location p2) {
    return DoubleMath.fuzzyEquals(p1.longitude, p2.longitude, TOLERANCE) &&
        DoubleMath.fuzzyEquals(p1.latitude, p2.latitude, TOLERANCE) &&
        DoubleMath.fuzzyEquals(p1.depth, p2.depth, TOLERANCE);
  }

  /**
   * Compute the {@link Bounds} of the supplied iterable. If {@code locs}
   * contains only 1 element, {@link Bounds#min} and {@link Bounds#max} of the
   * returned object will be the same.
   *
   * @param locs to compute bounds for
   * @throws IllegalArgumentException if {@code locs} is empty
   */
  public static Bounds bounds(Iterable<Location> locs) {
    checkArgument(locs.iterator().hasNext(), "Locations may not be empty");
    double minLon = Double.POSITIVE_INFINITY;
    double maxLon = Double.NEGATIVE_INFINITY;
    double minLat = Double.POSITIVE_INFINITY;
    double maxLat = Double.NEGATIVE_INFINITY;
    for (Location loc : locs) {
      minLon = loc.longitude < minLon ? loc.longitude : minLon;
      maxLon = loc.longitude > maxLon ? loc.longitude : maxLon;
      minLat = loc.latitude < minLat ? loc.latitude : minLat;
      maxLat = loc.latitude > maxLat ? loc.latitude : maxLat;
    }
    return new Bounds(minLat, minLon, maxLat, maxLon);
  }

  /**
   * Computes a centroid for a group of {@code Location}s as the average of
   * latitude, longitude, and depth;
   *
   * @param locs locations to process
   */
  public static Location centroid(Iterable<Location> locs) {
    double cLon = 0.0;
    double cLat = 0.0;
    double cDepth = 0.0;
    int size = 0;
    for (Location loc : locs) {
      cLon += loc.longitude;
      cLat += loc.latitude;
      cDepth += loc.depth;
      size++;
    }
    return Location.create(cLat / size, cLon / size, cDepth / size);
  }

  /**
   * Return a closed, straight-line {@link Path2D} representation of the
   * supplied list, ignoring depth.
   */
  public static Path2D toPath(LocationList locs) {
    Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD, locs.size());
    boolean starting = true;
    for (Location loc : locs) {
      double lon = loc.longitude;
      double lat = loc.latitude;
      // if just starting, then moveTo
      if (starting) {
        path.moveTo(lon, lat);
        starting = false;
        continue;
      }
      path.lineTo(lon, lat);
    }
    path.closePath();
    return path;
  }

  /**
   * Return the point in a {@code LocationList} that is that closest to the
   * supplied {@code Location}. This method uses
   * {@link #horzDistanceFast(Location, Location)} to compute the distance.
   * 
   * @param loc @code Location} of interest
   * @param locs {@code LocationList} to search for closest point in
   * @see #horzDistanceFast(Location, Location)
   */
  public static Location closestPoint(Location loc, LocationList locs) {
    double rMin = Double.POSITIVE_INFINITY;
    Location closest = locs.first();
    for (Location p : locs) {
      double r = horzDistanceFast(loc, p);
      if (r < rMin) {
        rMin = r;
        closest = p;
      }
    }
    return closest;
  }

  /**
   * Compute the horizontal distance (in km) from a {@code Location} to the
   * closest point in a {@code LocationList}. This method uses
   * {@link #horzDistanceFast(Location, Location)} to compute the distance.
   *
   * @param loc {@code Location} of interest
   * @param locs {@code LocationList} to compute distance to
   * @see #horzDistanceFast(Location, Location)
   */
  public static double minDistanceToLocations(Location loc, LocationList locs) {
    double rMin = Double.POSITIVE_INFINITY;
    for (Location p : locs) {
      double r = horzDistanceFast(loc, p);
      if (r < rMin) {
        rMin = r;
      }
    }
    return rMin;
  }

  /**
   * Compute the shortest horizontal distance (in km) from a {@code Location} to
   * the line defined by connecting the points in a {@code LocationList}. This
   * method uses
   * {@link Locations#distanceToSegmentFast(Location, Location, Location)} and
   * is inappropriate for for use at large separations (e.g. >200 km).
   *
   * @param loc {@code Location} of interest
   * @param locs {@code LocationList} to compute distance to
   * @see #distanceToSegmentFast(Location, Location, Location)
   */
  public static double minDistanceToLine(Location loc, LocationList locs) {
    if (locs.size() == 1) {
      return horzDistanceFast(loc, locs.get(0));
    }
    double min = Double.POSITIVE_INFINITY;
    for (int i = 0; i < locs.size() - 1; i++) {
      min = Math.min(min, distanceToSegmentFast(locs.get(i), locs.get(i + 1), loc));
    }
    return min;
  }

  /**
   * Compute the segment index that is closest to a {@code Location}. There are
   * {@code locs.size() - 1} segment indices. The indices of the segment
   * endpoints in the original location list are {@code [n, n+1]}.
   *
   * @param loc {@code Location} of interest
   * @param locs {@code LocationList} for which to compute the closest segment
   *        index
   * @throws IllegalArgumentException if {@code locs.size() < 2}
   */
  public static int minDistanceIndex(Location loc, LocationList locs) {
    checkArgument(locs.size() > 1);
    double min = Double.POSITIVE_INFINITY;
    int minIndex = -1;
    for (int i = 0; i < locs.size() - 1; i++) {
      double dist = distanceToSegmentFast(locs.get(i), locs.get(i + 1), loc);
      if (dist < min) {
        min = dist;
        minIndex = i;
      }
    }
    return minIndex;
  }

  /**
   * Return a radial distance {@code Location} filter.
   *
   * @param origin of filter
   * @param distance beyond which the filter will return {@code false}
   */
  public static Predicate<Location> distanceFilter(Location origin, double distance) {
    return new DistanceFilter(origin, distance);
  }

  /**
   * Return a radial distance {@code Location} filter that preprocesses
   * {@code Location}s through a {@link #rectangleFilter(Location, double)}.
   *
   * @param origin of filter
   * @param distance beyond which the filter will return {@code false}
   * @see #rectangleFilter(Location, double)
   */
  public static Predicate<Location> distanceAndRectangleFilter(Location origin, double distance) {
    return new RectangleAndDistanceFilter(origin, distance);
  }

  /**
   * Return a rectangular {@code Location} filter. The filter is definied in
   * geographic (lat,lon) space and is constrained to
   * {@link Coordinates#LAT_RANGE} and {@link Coordinates#LON_RANGE}. The filter
   * has dimensions of {@code 2 * distance} for both height and width, and is
   * centered on the supplied {@code Location} . This filter is for use as a
   * fast, first-pass filter before more computationally intensive distance
   * filtering.
   *
   * @param origin (center) of filter
   * @param distance half-width and half-height of rectangle outside of which
   *        the filter will return {@code false},
   * @see Coordinates
   */
  public static Predicate<Location> rectangleFilter(Location origin, double distance) {
    return new RectangleFilter(origin, distance);
  }

  private static class RectangleFilter implements Predicate<Location> {
    private final Rectangle2D rect;

    private RectangleFilter(Location origin, double distance) {
      rect = rectangle(origin, distance);
    }

    @Override
    public boolean apply(Location loc) {
      return rect.contains(loc.longitude, loc.latitude);
    }

    @Override
    public String toString() {
      return "Locations.RectangleFilter";
    }

  }

  private static class DistanceFilter implements Predicate<Location> {
    private final Location origin;
    private final double distance;

    private DistanceFilter(Location origin, double distance) {
      this.origin = origin;
      this.distance = distance;
    }

    @Override
    public boolean apply(Location loc) {
      return horzDistanceFast(origin, loc) <= distance;
    }

    @Override
    public String toString() {
      return "Locations.DistanceFilter " + filterInfo();
    }

    String filterInfo() {
      return "[origin: " + origin + ", distance: " + distance + "]";
    }
  }

  private static class RectangleAndDistanceFilter implements Predicate<Location> {
    private final RectangleFilter rectFilter;
    private final DistanceFilter distFilter;

    private RectangleAndDistanceFilter(Location origin, double distance) {
      rectFilter = new RectangleFilter(origin, distance);
      distFilter = new DistanceFilter(origin, distance);
    }

    @Override
    public boolean apply(Location loc) {
      return rectFilter.apply(loc) && distFilter.apply(loc);
    }

    @Override
    public String toString() {
      return "Locations.RectangleAndDistanceFilter " + distFilter.filterInfo();
    }
  }

  /*
   * Create a geographic (Mercator) {@link Rectangle2D} with coordinates in
   * decimal degrees that is centered on {@code loc} and has a width and height
   * of {@code 2 * distance}. The returned rectangle is intended for use in
   * quick contains operations and is constrained to minimum and maximum
   * longitudes and latitudes {@see GeoTools}.
   */
  private static Rectangle2D rectangle(Location loc, double distance) {
    double Δlon = distance * Coordinates.degreesLonPerKm(loc);
    double Δlat = distance * Coordinates.degreesLatPerKm(loc);
    double minLon = max(loc.longitude - Δlon, LON_RANGE.lowerEndpoint());
    double maxLon = min(loc.longitude + Δlon, LON_RANGE.upperEndpoint());
    double minLat = max(loc.latitude - Δlat, LAT_RANGE.lowerEndpoint());
    double maxLat = min(loc.latitude + Δlat, LAT_RANGE.upperEndpoint());
    return new Rectangle2D.Double(minLon, minLat, maxLon - minLon, maxLat - minLat);
  }
  
  /* Helper method for LocationList implementations */
  static String toStringHelper(LocationList locations) {
    return NEWLINE + Joiner.on(NEWLINE).join(locations) + NEWLINE;
  }

}
