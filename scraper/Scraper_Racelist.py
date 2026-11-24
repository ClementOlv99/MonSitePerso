import datetime
from io import StringIO
import pandas as pd
from seleniumbase import SB
from bs4 import BeautifulSoup

BASE = "https://www.procyclingstats.com/races.php?p=uci&s=year-calendar&year={}&circuit={}&class=&filter=Filter"
CIRCUITS = {"UWT": 1, "ProSeries": 26}

def to_iso(day_month, year):
    day, month = day_month.strip().split(".")
    return f"{year}-{month}-{day}"


def scrape_races(year):
    frames = []

    with SB(uc=True) as sb:
        sb.activate_cdp_mode()
        for name, circuit in CIRCUITS.items():
            sb.goto(BASE.format(year, circuit))
            sb.wait_for_element("table.basic", timeout=120)
            html = sb.get_page_source()

            df = pd.read_html(StringIO(html), attrs={"class": "basic"}, extract_links="body")[0]

            soup = BeautifulSoup(html, "lxml")
            countries = []
            for row in soup.select("table.basic tbody tr"):
                span = row.select_one("span.flag")
                if span is None:
                    countries.append(None)
                else:
                    classes = span.get("class")
                    countries.append(next((c for c in classes if c != "flag"), None))

            if len(countries) != len(df):
                raise ValueError(f"{name}: {len(countries)} flags for {len(df)} rows")

            df["Country"] = countries
            frames.append(df)

            sb.sleep(3)

    dfRaces = pd.concat(frames, ignore_index=True)

    dfRaces["RaceName"] = dfRaces["Race"].str[0]
    dfRaces["RaceURL"] = dfRaces["Race"].str[1]
    dfRaces["Dates"] = dfRaces["Date"].str[0]
    dfRaces["Class"] = dfRaces["Class"].str[0]
    dfRaces["startDate"] = dfRaces["Dates"]
    dfRaces["endDate"] = dfRaces["Dates"]

    dfRaces = dfRaces.drop(columns=["Winner", "Race", "Date.1", "Date"])

    for i in range(len(dfRaces)):

        dates = dfRaces.at[i, "Dates"].split("-")

        # CHANGED: strip spaces + add the year in ISO format
        dfRaces.at[i, "startDate"] = to_iso(dates[0], year)

        if len(dates) == 2:
            dfRaces.at[i, "endDate"] = to_iso(dates[1], year)
        else:
            dfRaces.at[i, "endDate"] = to_iso(dates[0], year)

        dfRaces.at[i, "Class"] = dfRaces.at[i, "Class"][2:]

        match dfRaces.at[i, "RaceName"]:
            case "Tour de France":
                dfRaces.at[i, "Class"] = "TDF"
            case "Giro d'Italia":
                dfRaces.at[i, "Class"] = "GIRO"
            case "La Vuelta Ciclista a España":
                dfRaces.at[i, "Class"] = "VUELTA"
            case "Milano-Sanremo" | "Paris-Roubaix Hauts-de-France" | "Liège-Bastogne-Liège" | "Il Lombardia" | "Ronde van Vlaanderen ME":
                dfRaces.at[i, "Class"] = "Monument"

    dfRaces = dfRaces.rename(columns={
        "RaceURL": "raceUrl",
        "RaceName": "name",
        "Class": "category",
        "Country": "country",
    })

    dfRaces = dfRaces.drop(columns=["Dates"])
    
    return dfRaces    


