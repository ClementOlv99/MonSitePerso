import pandas as pd
from seleniumbase import SB
from bs4 import BeautifulSoup

BASE = "https://www.procyclingstats.com/{}/startlist"

def to_iso(day_month, year):
    day, month = day_month.strip().split(".")
    return f"{year}-{month}-{day}"


def scrape_startlist(race_path):
    records = []

    with SB(uc=True) as sb:
        sb.activate_cdp_mode()
        sb.goto(BASE.format(race_path))
        sb.wait_for_element("ul.startlist_v4", timeout=15)
        html = sb.get_page_source()

    soup = BeautifulSoup(html, "html.parser")
    startlist = soup.find("ul", class_="startlist_v4")

    if not startlist:
        return pd.DataFrame()


    for team_li in startlist.find_all("li", recursive=False):
        team_tag = team_li.find("a", class_="team")
        team_name = team_tag.get_text(strip=True) if team_tag else "Unknown"


        rider_list = team_li.find("ul")
        if not rider_list:
            continue

        for rider_li in rider_list.find_all("li"):
            country = None
            bib_tag = rider_li.find("span", class_="bib")
            rider_tag = rider_li.find("a")
            country_tag = rider_li.find("span", class_="flag")

            if country_tag:
                classes = [c for c in country_tag.get("class", []) if c != "flag"]
                country = classes[0] if classes else None

            if rider_tag:
                records.append({
                    "team": team_name,
                    "rider": rider_tag.get_text(strip=True),
                    "riderUrl": rider_tag.get("href"),
                    "country" : country,
                    "raceUrl" : race_path
                })

    return pd.DataFrame(records)


if __name__ == "__main__":
    df = scrape_startlist("race/tour-down-under-2027-gc")
    print(df)
    df.to_json("Startlist.json", orient="records", force_ascii=False, indent=2)
