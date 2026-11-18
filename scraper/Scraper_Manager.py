import datetime

import pandas as pd
import requests

from Scraper_Racelist import scrape_races
from Scraper_Startlist import scrape_startlist

API_BASE = "http://localhost:8080/api"


def get_season_year():
    """From November on, target next season"""
    today = datetime.date.today()
    if (today.month == 11) or (today.month == 12):
        return today.year + 1
    else:
        return today.year

def get_today():
    today = datetime.date.today()
    if (today.month == 11) or (today.month == 12):
        today = datetime.date(today.year+1, 1, 1)
        #today = datetime.date(2026, 4, 18)
        return today
    else:
        return today

def is_within_a_month(iso_date):
    reference = get_today()
    date = datetime.date.fromisoformat(iso_date)
    limit = (pd.Timestamp(reference) + pd.DateOffset(months=1)).date()
    return reference <= date <= limit



def to_payload(df):
    clean = df.astype(object).where(pd.notna(df), None)
    return clean.to_dict(orient="records")


def send(endpoint, df):
    """POST a DataFrame to the Spring backend and check the response."""
    payload = to_payload(df)
    url = API_BASE + endpoint

    try:
        response = requests.post(url, json=payload, timeout=30)
    except requests.ConnectionError:
        raise SystemExit(f"Cannot reach {url}: is the Spring Boot backend running?")

    if response.status_code != 201:
        raise SystemExit(f"{url} answered {response.status_code}: {response.text}")

    imported = response.json().get("imported")
    print(f"{endpoint}: sent {len(payload)}, backend imported {imported}")
    if imported != len(payload):
        print("Warning: imported count differs from sent count")


def main():
    year = get_season_year()
    print(f"Season: {year}")

    # 1. Race list (future scrapers may depend on it)
    races  = scrape_races(year)
    print(f"Scraped {len(races)} races")
    races.to_json("Races.json", orient="records", force_ascii=False, indent=2)  # local backup for debugging
    send("/races", races)


    startlist = pd.DataFrame()

    for i in range(len(races)):
        if is_within_a_month(races.at[i, "startDate"]):
            current = scrape_startlist(races.at[i, "raceUrl"])
            startlist = pd.concat([startlist, current], join='outer')
        
    
    startlist.to_json("Startlist.json", orient="records", force_ascii=False, indent=2)
    (send("/participation", startlist))





if __name__ == "__main__":
    main()