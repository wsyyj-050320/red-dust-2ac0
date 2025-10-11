#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import argparse
import csv
import math
import sys
from dataclasses import dataclass
from typing import List, Optional, Tuple, Dict


@dataclass
class Station:
    name: str
    angle_deg: float  # measured interior/turning angle in decimal degrees
    distance_m: float  # distance of outgoing side from this station to the next

    # Computed fields (filled later)
    corrected_angle_deg: float = 0.0
    azimuth_deg: float = 0.0  # outgoing side azimuth from this station
    dx: float = 0.0
    dy: float = 0.0
    dx_corrected: float = 0.0
    dy_corrected: float = 0.0


@dataclass
class TraverseResult:
    stations: List[Station]
    start_coordinates: Tuple[float, float]
    point_coordinates: List[Tuple[float, float]]  # coordinates at each vertex, including final point
    angle_misclosure_deg: float
    fx: float
    fy: float
    fD: float
    relative_precision: float


# ---------- Angle utilities ----------

def dms_to_deg(d: float, m: float, s: float) -> float:
    sign = 1.0
    if d < 0 or m < 0 or s < 0:
        # treat any negative part as negative angle
        sign = -1.0
    return sign * (abs(d) + abs(m) / 60.0 + abs(s) / 3600.0)


def parse_angle_value(value: str) -> float:
    """Parse angle from formats like '45', '45.5', '45-30-0', '45 30 0', '45°30′0″'."""
    v = value.strip()
    if not v:
        return 0.0
    # Replace common separators
    v = (
        v.replace("°", " ")
        .replace("′", " ")
        .replace("'", " ")
        .replace("\"", " ")
        .replace("″", " ")
        .replace("-", " ")
        .replace(":", " ")
    )
    parts = [p for p in v.split() if p]
    try:
        if len(parts) == 1:
            return float(parts[0])
        elif len(parts) == 2:
            d, m = float(parts[0]), float(parts[1])
            return dms_to_deg(d, m, 0.0)
        else:
            d, m, s = float(parts[0]), float(parts[1]), float(parts[2])
            return dms_to_deg(d, m, s)
    except ValueError:
        raise ValueError(f"无法解析角度值: '{value}'")


def deg_to_dms(angle_deg: float) -> Tuple[int, int, float]:
    """Convert decimal degrees to DMS with seconds rounded to 0.01."""
    a = angle_deg
    # Normalize for representation, but keep sign separately for components
    sign = -1 if a < 0 else 1
    a = abs(a)
    d = int(math.floor(a))
    m_float = (a - d) * 60.0
    m = int(math.floor(m_float))
    s = (m_float - m) * 60.0
    # Rounding
    s = round(s, 2)
    # Handle rounding overflow
    if s >= 60.0:
        s -= 60.0
        m += 1
    if m >= 60:
        m -= 60
        d += 1
    d *= sign
    return d, m, s


def format_dms(angle_deg: float) -> str:
    d, m, s = deg_to_dms(angle_deg)
    return f"{d:>3d}°{m:02d}′{s:05.2f}″"


def normalize_azimuth(angle_deg: float) -> float:
    a = angle_deg % 360.0
    if a < 0:
        a += 360.0
    return a


# ---------- Core computation ----------

def compute_closed_traverse(
    stations: List[Station],
    start_x: float,
    start_y: float,
    initial_azimuth_deg: float,
) -> TraverseResult:
    n = len(stations)
    if n < 3:
        raise ValueError("至少需要3个测站形成闭合导线")

    # 1) Angle misclosure and correction
    measured_sum = sum(s.angle_deg for s in stations)
    theoretical_sum = (n - 2) * 180.0
    angle_misclosure = measured_sum - theoretical_sum
    correction_per_angle = -angle_misclosure / n

    for s in stations:
        s.corrected_angle_deg = s.angle_deg + correction_per_angle

    # 2) Azimuth propagation
    stations[0].azimuth_deg = normalize_azimuth(initial_azimuth_deg)
    for i in range(0, n - 1):
        next_az = stations[i].azimuth_deg + 180.0 - stations[i].corrected_angle_deg
        stations[i + 1].azimuth_deg = normalize_azimuth(next_az)

    # 3) Unadjusted coordinate increments
    for s in stations:
        rad = math.radians(s.azimuth_deg)
        s.dx = s.distance_m * math.cos(rad)
        s.dy = s.distance_m * math.sin(rad)

    sum_dx = sum(s.dx for s in stations)
    sum_dy = sum(s.dy for s in stations)
    sum_D = sum(s.distance_m for s in stations)

    fx = sum_dx  # should be ~ 0 for closed traverse
    fy = sum_dy
    fD = math.hypot(fx, fy)
    relative_precision = float('inf') if fD == 0 else sum_D / fD

    # 4) Bowditch correction proportional to length
    if sum_D <= 0:
        raise ValueError("距离和为0，无法进行Bowditch改正")

    for s in stations:
        weight = s.distance_m / sum_D
        s.dx_corrected = s.dx - fx * weight
        s.dy_corrected = s.dy - fy * weight

    # 5) Coordinates accumulation (start point + each outgoing side)
    coords: List[Tuple[float, float]] = [(start_x, start_y)]
    x, y = start_x, start_y
    for s in stations:
        x += s.dx_corrected
        y += s.dy_corrected
        coords.append((x, y))

    return TraverseResult(
        stations=stations,
        start_coordinates=(start_x, start_y),
        point_coordinates=coords,
        angle_misclosure_deg=angle_misclosure,
        fx=fx,
        fy=fy,
        fD=fD,
        relative_precision=relative_precision,
    )


# ---------- I/O ----------

_EXPECTED_COLUMNS = {
    "station",
    "name",
    "pt",
    "angle",
    "deg",
    "min",
    "sec",
    "distance",
    "D",
}


def _normalize_key(key: str) -> str:
    return key.strip().lower()


def read_stations_from_csv(path: str) -> List[Station]:
    stations: List[Station] = []
    with open(path, "r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        header = {_normalize_key(h): h for h in reader.fieldnames or []}
        # Heuristics for column names
        key_station = header.get("station") or header.get("name") or header.get("pt")
        key_angle = header.get("angle")
        key_deg = header.get("deg")
        key_min = header.get("min")
        key_sec = header.get("sec")
        key_distance = header.get("distance") or header.get("d")

        if key_station is None:
            raise ValueError(
                "CSV缺少' station/name/pt '列之一用于点号"
            )
        if key_distance is None:
            raise ValueError("CSV缺少 'distance' 或 'D' 列")
        if not key_angle and not key_deg:
            raise ValueError("CSV需提供 'angle' 或 (deg,min,sec) 列")

        for row in reader:
            name = (row.get(key_station) or "").strip()
            if not name:
                continue

            # Angle parsing
            if key_angle:
                angle_deg = parse_angle_value(row.get(key_angle, "0"))
            else:
                d = float(row.get(key_deg, "0") or 0)
                m = float(row.get(key_min, "0") or 0)
                s = float(row.get(key_sec, "0") or 0)
                angle_deg = dms_to_deg(d, m, s)

            distance_str = (row.get(key_distance) or "0").strip()
            try:
                distance = float(distance_str)
            except ValueError:
                raise ValueError(f"距离无法解析: '{distance_str}' (站点 {name})")

            stations.append(Station(name=name, angle_deg=angle_deg, distance_m=distance))

    if len(stations) < 3:
        raise ValueError("CSV中站点不足3个，无法形成闭合导线")

    return stations


def write_results_to_csv(result: TraverseResult, path: str) -> None:
    stations = result.stations
    coords = result.point_coordinates

    fieldnames = [
        "点号",
        "水平角_度",
        "水平角_分",
        "水平角_秒",
        "改正角_度",
        "改正角_分",
        "改正角_秒",
        "坐标方位角α_度",
        "坐标方位角α_分",
        "坐标方位角α_秒",
        "距离D_m",
        "Δx",
        "Δy",
        "改正后Δx",
        "改正后Δy",
        "x/m",
        "y/m",
    ]

    with open(path, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()

        # First row is starting point coordinates (no angle/distance)
        start_x, start_y = result.start_coordinates
        writer.writerow(
            {
                "点号": stations[0].name,
                "x/m": f"{start_x:.3f}",
                "y/m": f"{start_y:.3f}",
            }
        )

        # For each leg i from station i to i+1
        for i, s in enumerate(stations):
            a_d, a_m, a_s = deg_to_dms(s.angle_deg)
            c_d, c_m, c_s = deg_to_dms(s.corrected_angle_deg)
            z_d, z_m, z_s = deg_to_dms(s.azimuth_deg)
            x_i, y_i = coords[i + 1]
            writer.writerow(
                {
                    "点号": stations[(i + 1) % len(stations)].name,
                    "水平角_度": a_d,
                    "水平角_分": a_m,
                    "水平角_秒": f"{a_s:.2f}",
                    "改正角_度": c_d,
                    "改正角_分": c_m,
                    "改正角_秒": f"{c_s:.2f}",
                    "坐标方位角α_度": z_d,
                    "坐标方位角α_分": z_m,
                    "坐标方位角α_秒": f"{z_s:.2f}",
                    "距离D_m": f"{s.distance_m:.3f}",
                    "Δx": f"{s.dx:.4f}",
                    "Δy": f"{s.dy:.4f}",
                    "改正后Δx": f"{s.dx_corrected:.4f}",
                    "改正后Δy": f"{s.dy_corrected:.4f}",
                    "x/m": f"{x_i:.3f}",
                    "y/m": f"{y_i:.3f}",
                }
            )

        # Summary row
        writer.writerow(
            {
                "点号": "Σ/总结",
                "水平角_度": "",
                "水平角_分": "",
                "水平角_秒": "",
                "改正角_度": "",
                "改正角_分": "",
                "改正角_秒": "",
                "坐标方位角α_度": "",
                "坐标方位角α_分": "",
                "坐标方位角α_秒": "",
                "距离D_m": "",
                "Δx": f"fx={result.fx:.4f}",
                "Δy": f"fy={result.fy:.4f}",
                "改正后Δx": f"fD={result.fD:.4f}",
                "改正后Δy": f"k=1:{result.relative_precision:,.0f}" if math.isfinite(result.relative_precision) else "∞",
                "x/m": f"∑角闭合={result.angle_misclosure_deg:.6f}°",
                "y/m": "",
            }
        )


def print_table(result: TraverseResult) -> None:
    stations = result.stations
    coords = result.point_coordinates

    def row(*cols: str) -> None:
        print(" ".join(c.ljust(w) for c, w in zip(cols, widths)))

    headers = [
        "点号",
        "水平角(° ′ ″)",
        "改正角(° ′ ″)",
        "坐标方位角α(° ′ ″)",
        "D/m",
        "Δx/m",
        "Δy/m",
        "Δx'/m",
        "Δy'/m",
        "x/m",
        "y/m",
    ]
    widths = [6, 16, 16, 18, 10, 12, 12, 12, 12, 12, 12]

    print("".join(["-" for _ in range(sum(widths) + len(widths) - 1)]))
    row(*headers)
    print("".join(["-" for _ in range(sum(widths) + len(widths) - 1)]))

    # Starting point line
    row(
        stations[0].name,
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        "",
        f"{result.start_coordinates[0]:.3f}",
        f"{result.start_coordinates[1]:.3f}",
    )

    for i, s in enumerate(stations):
        row(
            stations[(i + 1) % len(stations)].name,
            format_dms(s.angle_deg),
            format_dms(s.corrected_angle_deg),
            format_dms(s.azimuth_deg),
            f"{s.distance_m:.3f}",
            f"{s.dx:.4f}",
            f"{s.dy:.4f}",
            f"{s.dx_corrected:.4f}",
            f"{s.dy_corrected:.4f}",
            f"{coords[i + 1][0]:.3f}",
            f"{coords[i + 1][1]:.3f}",
        )

    print("".join(["-" for _ in range(sum(widths) + len(widths) - 1)]))
    print(
        f"角度闭合差: {result.angle_misclosure_deg:.6f}° | fx = {result.fx:.4f} m, fy = {result.fy:.4f} m, fD = {result.fD:.4f} m, k = 1:{result.relative_precision:,.0f}"
    )


# ---------- CLI ----------

def build_arg_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description=(
            "闭合导线（Bowditch）计算器：输入各测站水平角与边长，给定首边坐标方位角与起点坐标，自动计算改正角、各边坐标方位角、坐标增量、Bowditch改正与坐标值。"
        )
    )
    p.add_argument(
        "--input",
        required=True,
        help="CSV文件路径，包含列: station/name, angle或(deg,min,sec), distance",
    )
    p.add_argument("--start-x", type=float, required=True, help="起点x坐标")
    p.add_argument("--start-y", type=float, required=True, help="起点y坐标")
    p.add_argument(
        "--alpha0",
        type=parse_angle_value,
        required=True,
        help="首边坐标方位角(°)。可用小数或DMS(如45-0-0)",
    )
    p.add_argument(
        "--output",
        help="输出CSV路径（可选）。若提供则写入详细结果表。",
    )
    return p


def main(argv: Optional[List[str]] = None) -> int:
    parser = build_arg_parser()
    args = parser.parse_args(argv)

    try:
        stations = read_stations_from_csv(args.input)
        result = compute_closed_traverse(
            stations=stations,
            start_x=args.start_x,
            start_y=args.start_y,
            initial_azimuth_deg=args.alpha0,
        )
    except Exception as e:
        print(f"错误: {e}", file=sys.stderr)
        return 2

    print_table(result)
    if args.output:
        try:
            write_results_to_csv(result, args.output)
            print(f"已写入: {args.output}")
        except Exception as e:
            print(f"写入输出失败: {e}", file=sys.stderr)
            return 3

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
