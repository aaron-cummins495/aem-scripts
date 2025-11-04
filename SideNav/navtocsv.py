import pandas as pd
import requests
from bs4 import BeautifulSoup
import json
import os
from urllib.parse import urlparse

# Config
TEMPLATE_PATH = "/conf/au/settings/dam/cfm/models/side-nav-cf-model"
INPUT_EXCEL = "/Users/raghvendrasingh/TA/AU/Migration/SideNav/input.xlsx"   # Excel with a column "URL"
OUTPUT_XLSX = "output.xlsx"
FAILED_LOG = "failed_urls.txt"
BASE_CF_PATH = "/content/dam/au/cf"
BASE_PAGE_PATH = "/content/au"

def extract_sidenav_json(nav_html):
    """Convert <nav> HTML into sideNavLinksCMF JSON (one line per object)"""
    soup = BeautifulSoup(nav_html, "html.parser")
    json_items = []

    def process_href(href):
        if not href or href == "#":
            return href
        parsed = urlparse(href)
        if parsed.netloc == "" and href.startswith("/"):
            href = f"{BASE_PAGE_PATH}{href}"
        href = os.path.splitext(href)[0]
        return href

    def parse_li(li_tag):
        link = li_tag.find('a')
        if not link:
            return None
        title = link.get_text(strip=True)
        href = process_href(link.get('href', '#'))

        nested_ul = li_tag.find('ul', recursive=False)
        nested_links = []
        if nested_ul:
            for child_li in nested_ul.find_all('li', recursive=False):
                child_link = child_li.find('a')
                if child_link:
                    nested_links.append({
                        "nested_sideNavTitle": child_link.get_text(strip=True),
                        "nested_sideNavLink": process_href(child_link.get('href', '#'))
                    })

        return {
            "sideNavTitleCMF": title,
            "sideNavLinkCMF": "#" if nested_ul else href,
            "nested_sideNavLinks2CMF": nested_links
        }

    for li in soup.select("ul#nav-accordion-holder > li"):
        item = parse_li(li)
        if item:
            json_items.append(json.dumps(item, separators=(',', ':')))

    return "\n".join(json_items)

def convert_url_to_path(url):
    parsed = urlparse(url)
    path = parsed.path
    dir_path = os.path.dirname(path)
    new_path = f"{BASE_CF_PATH}{dir_path}"
    return new_path

def get_page_name(url):
    path = urlparse(url).path
    filename = os.path.basename(path)
    page_name, _ = os.path.splitext(filename)
    return page_name

# --- Main Script ---
df = pd.read_excel(INPUT_EXCEL)
rows = []
failed_urls = []

for url in df['URL']:
    try:
        headers = {"x-user-agent": "AU-AEM-Importer"}
        r = requests.get(url, headers=headers, timeout=10)
        r.raise_for_status()  # will raise HTTPError for 404/500, etc.

        soup = BeautifulSoup(r.text, "html.parser")
        nav = soup.find("nav", {"id": "left-navigation"})
        if not nav:
            failed_urls.append(f"No nav found: {url}")
            continue

        side_nav_json = extract_sidenav_json(str(nav))
        converted_path = convert_url_to_path(url)
        name = get_page_name(url)
        title_tag = soup.find("title")
        title = title_tag.get_text(strip=True) if title_tag else name

        rows.append({
            "path": converted_path,
            "name": name,
            "title": title,
            "template": TEMPLATE_PATH,
            "sideNavLinksCMF": side_nav_json
        })

    except requests.exceptions.RequestException as e:
        failed_urls.append(f"{url} -> {str(e)}")
        continue

# Save results
out_df = pd.DataFrame(rows)
out_df.to_excel(OUTPUT_XLSX, index=False)
print(f"✅ Output written to {OUTPUT_XLSX}")

# Save failed URLs
if failed_urls:
    with open(FAILED_LOG, "w") as f:
        f.write("\n".join(failed_urls))
    print(f"⚠️ Logged {len(failed_urls)} failed URLs to {FAILED_LOG}")
else:
    print("🎉 No failed URLs.")
