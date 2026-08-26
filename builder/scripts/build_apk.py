import urllib.request
import urllib.error
import re
import sys
import os
import subprocess
import json
import hashlib

def resolve_custom_urls(custom_input, targets, default_urls):
    if not custom_input:
        return default_urls
    
    raw_urls = [u.strip() for u in custom_input.split(",") if u.strip().startswith("http")]
    if not raw_urls:
        return default_urls

    app_url_map = dict(default_urls)
    unmatched_urls = []

    for u in raw_urls:
        u_lower = u.lower()
        if "youtube.music" in u_lower or "youtube-music" in u_lower or "ytmusic" in u_lower:
            app_url_map["YouTube-Music"] = u
        elif "reddit" in u_lower:
            app_url_map["Reddit"] = u
        elif "youtube" in u_lower:
            app_url_map["YouTube"] = u
        elif "twitter" in u_lower or "x.com" in u_lower:
            app_url_map["Twitter"] = u
        elif "spotify" in u_lower:
            app_url_map["Spotify"] = u
        elif "instagram" in u_lower:
            app_url_map["Instagram"] = u
        elif "twitch" in u_lower:
            app_url_map["Twitch"] = u
        elif "tiktok" in u_lower or "musically" in u_lower:
            app_url_map["TikTok"] = u
        else:
            unmatched_urls.append(u)

    if unmatched_urls:
        if len(targets) == 1 and len(unmatched_urls) == 1:
            app_url_map[targets[0]] = unmatched_urls[0]
        else:
            for idx, u in enumerate(unmatched_urls):
                if idx < len(targets):
                    app_url_map[targets[idx]] = u

    return app_url_map

def download_apk(url, dest_file):
    print(f"Downloading from URL: {url}")
    user_agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    headers = {
        "User-Agent": user_agent,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.5"
    }

    cookie_jar = urllib.request.HTTPCookieProcessor()
    opener = urllib.request.build_opener(cookie_jar)

    curr_url = url
    for step in range(5):
        h = dict(headers, Referer=curr_url)
        req = urllib.request.Request(curr_url, headers=h)
        with opener.open(req) as resp:
            content_type = resp.headers.get("Content-Type", "").lower()
            first_chunk = resp.read(4096)
            if first_chunk.startswith(b"PK\x03\x04") or "package-archive" in content_type or "octet-stream" in content_type or "application/zip" in content_type:
                with open(dest_file, "wb") as f:
                    f.write(first_chunk)
                    while True:
                        chunk = resp.read(2 * 1024 * 1024)
                        if not chunk:
                            break
                        f.write(chunk)
                
                sz = os.path.getsize(dest_file) / (1024 * 1024)
                if sz > 2.0:
                    print(f"✅ Downloaded verified APK package: {sz:.2f} MB")
                    return
                else:
                    print(f"Notice: Downloaded stream too small ({sz:.2f} MB)")

            # Parse HTML to find next download link
            html = (first_chunk + resp.read()).decode("utf-8", errors="ignore")
            
            # 1. Search for download.php token link
            matches = re.findall(r'href=[\"\']([^\"\']*download\.php\?[^\"\']*)[\"\']', html)
            if not matches:
                # 2. Search for download button link
                matches = re.findall(r'href=[\"\']([^\"\']*(?:apk-download\/download\/\?|downloading\/\?)[^\"\']*)[\"\']', html)
            if not matches:
                matches = re.findall(r'href=[\"\']([^\"\']*(?:apk-download\/download)[^\"\']*)[\"\']', html)
            
            valid_matches = [m for m in matches if not any(ext in m for ext in [".css", ".js", ".png", ".jpg", ".svg"])]
            if not valid_matches:
                raise Exception(f"Failed to find download link on page: {curr_url}")
            
            sub_link = valid_matches[0].replace("&amp;", "&")
            curr_url = "https://www.apkmirror.com" + sub_link if sub_link.startswith("/") else sub_link
            print(f"Following download token (hop {step+1}): {curr_url[:80]}...")

    raise Exception(f"Failed to download APK after 5 hops for {url}")

def main():
    raw_inputs_json = os.environ.get("GITHUB_INPUTS", "{}")
    try:
        workflow_inputs = json.loads(raw_inputs_json)
    except Exception:
        workflow_inputs = {}

    app_choice = workflow_inputs.get("app_type", os.environ.get("INPUT_APP_TYPE", "All"))
    custom_url_input = workflow_inputs.get("custom_apk_url", os.environ.get("INPUT_CUSTOM_APK_URL", "")).strip()
    install_type = workflow_inputs.get("install_type", os.environ.get("INPUT_INSTALL_TYPE", "Non-Root (with GmsCore)"))
    arch = workflow_inputs.get("architecture", os.environ.get("INPUT_ARCHITECTURE", "all"))

    default_urls = {
        "YouTube": os.environ.get("DEFAULT_YOUTUBE_URL", "https://www.apkmirror.com/apk/google-inc/youtube/youtube-21-04-223-release/youtube-21-04-223-android-apk-download/download/?key=b87ce717c47f3920c139dae8e15df2ba744764e9&forcebaseapk=true"),
        "YouTube-Music": os.environ.get("DEFAULT_YTMUSIC_URL", "https://www.apkmirror.com/apk/google-inc/youtube-music/youtube-music-9-15-51-release/youtube-music-9-15-51-4-android-apk-download/download/?key=fed902f297975b9851e611188d3a3764d9217718&forcebaseapk=true"),
        "Reddit": os.environ.get("DEFAULT_REDDIT_URL", "https://www.apkmirror.com/apk/redditinc/reddit/reddit-2026-04-0-release/reddit-2026-04-0-2-android-apk-download/download/?key=599718010a8e24c93cdc558e266f71441aa9b417&forcebaseapk=true")
    }

    # Discover all options files present in config/
    available_apps = {}
    if os.path.exists("config"):
        for f in os.listdir("config"):
            if f.startswith("options-") and f.endswith(".json"):
                slug_name = f.replace("options-", "").replace(".json", "")
                if slug_name == "youtube":
                    available_apps["YouTube"] = f
                elif slug_name == "ytmusic":
                    available_apps["YouTube-Music"] = f
                elif slug_name == "reddit":
                    available_apps["Reddit"] = f
                else:
                    available_apps[slug_name.capitalize()] = f

    if app_choice.startswith("All"):
        targets = list(available_apps.keys())
    else:
        targets = [app_choice]

    resolved_urls = resolve_custom_urls(custom_url_input, targets, default_urls)
    os.makedirs("output", exist_ok=True)
    
    built_summary = []
    failed_summary = []

    for app in targets:
        print(f"\n=======================================================")
        print(f"  PROCESSING TARGET: {app}")
        print(f"=======================================================")
        
        try:
            apk_url = resolved_urls.get(app)
            if not apk_url:
                print(f"Notice: No URL configured for {app}. Skipping.")
                continue

            input_apk = f"input-{app}.apk"
            output_apk = f"output/Morphe-{app}.apk"

            download_apk(apk_url, input_apk)
            in_size = os.path.getsize(input_apk) / (1024*1024)
            print(f"✅ {app} stock APK ready: {in_size:.2f} MB")

            args = ["java", "-jar", "build-tools/morphe-desktop.jar", "patch"]
            args.extend(["-p", "build-tools/patches.mpp"])
            args.extend(["-o", output_apk])

            if arch != "all":
                args.append(f"--striplibs={arch}")

            slug = "ytmusic" if "music" in app.lower() else ("youtube" if "youtube" in app.lower() else app.lower().replace("-", ""))
            opts_json_file = f"config/options-{slug}.json"
            applied_patch_count = 0
            forced_enables = set()
            forced_disables = set()

            # Load options JSON file
            if os.path.exists(opts_json_file):
                with open(opts_json_file, "r") as f:
                    options_data = json.load(f)

                patches_dict = options_data[0].get("patches", {})

                # 1. Apply UI popup toggles for disabled patches if checked
                for key, val in workflow_inputs.items():
                    val_str = str(val).lower()
                    if val_str == "true":
                        key_clean = key.lower().replace("input_", "").replace("enable_", "")
                        # e.g. youtube_clone_app or ytmusic_clone_app
                        if key_clean.startswith(f"{slug}_"):
                            p_key_part = key_clean[len(slug)+1:]
                            for p_name in patches_dict:
                                p_slug = re.sub(r'[^a-zA-Z0-9_]', '_', p_name.lower())
                                if p_slug == p_key_part or p_key_part in p_slug:
                                    patches_dict[p_name]["enabled"] = True
                                    forced_enables.add(p_name)
                                    print(f"✅ [{app}] ENABLED via Popup Checkbox: {p_name}")

                # 2. Apply Root mode handling
                if install_type == "Root (without GmsCore)":
                    if "GmsCore support" in patches_dict:
                        patches_dict["GmsCore support"]["enabled"] = False
                        forced_disables.add("GmsCore support")

                # Force -e and -d flags explicitly to CLI
                for p_name, p_obj in patches_dict.items():
                    if p_obj.get("enabled", True):
                        forced_enables.add(p_name)
                    else:
                        forced_disables.add(p_name)

                # Remove conflicts
                for e in forced_enables:
                    forced_disables.discard(e)

                runtime_opts_file = f"/tmp/runtime_options_{slug}.json"
                options_data[0]["patches"] = patches_dict
                with open(runtime_opts_file, "w") as f:
                    json.dump(options_data, f, indent=4)

                args.append(f"--options-file={runtime_opts_file}")
                for e in sorted(forced_enables):
                    args.extend(["-e", e])
                for d in sorted(forced_disables):
                    args.extend(["-d", d])

                applied_patch_count = len(forced_enables)
                print(f"✅ Loaded Options JSON: {opts_json_file} ({applied_patch_count} patches enabled, {len(forced_disables)} disabled)")

            args.append(input_apk)
            print(f"Executing patcher for {app}...")
            res = subprocess.run(args)
            if res.returncode != 0:
                raise Exception(f"Morphe patcher exited with code {res.returncode}")
            
            out_size = os.path.getsize(output_apk) / (1024*1024)
            print(f"✅ {app} successfully patched! (Size: {out_size:.2f} MB)")
            built_summary.append({
                "name": app,
                "filename": f"Morphe-{app}.apk",
                "size": f"{out_size:.1f} MB",
                "patches_count": applied_patch_count
            })

        except Exception as e:
            print(f"\n⚠️ WARNING: Failed to process target [{app}]: {e}")
            print(f"👉 Continuing with remaining targets...")
            failed_summary.append({
                "name": app,
                "error": str(e)
            })

    # If all targets failed, exit with error
    if not built_summary:
        print("❌ Error: All target applications failed to build.")
        sys.exit(1)

    # Generate SHA256 Checksums
    checksum_lines = []
    with open("output/sha256sum.txt", "w") as chk_file:
        for f_name in sorted(os.listdir("output")):
            if f_name.endswith(".apk"):
                with open(os.path.join("output", f_name), "rb") as af:
                    h = hashlib.sha256(af.read()).hexdigest()
                    chk_file.write(f"{h}  {f_name}\n")
                    checksum_lines.append(f"- `{f_name}`: `{h}`")

    # Load Official Morphe Patches Release Notes / Changelog
    patches_changelog = "Official Morphe Patches Bundle applied."
    patches_tag = "Latest"
    if os.path.exists("build-tools/patches-release.json"):
        try:
            with open("build-tools/patches-release.json", "r") as f:
                rel_data = json.load(f)
                patches_tag = rel_data.get("tag_name", "Latest")
                patches_changelog = rel_data.get("body", "").strip()
        except Exception as e:
            print(f"Notice: Could not parse patches changelog: {e}")

    # Generate Rich GitHub Release Notes
    summary_table = [
        "| App | Output File | Size | Patches Applied |",
        "| :--- | :--- | :--- | :--- |"
    ]
    for b in built_summary:
        summary_table.append(f"| **{b['name']}** | `{b['filename']}` | {b['size']} | {b['patches_count']} patches |")

    failed_section = ""
    if failed_summary:
        failed_lines = [f"- **{f['name']}**: `{f['error']}`" for f in failed_summary]
        failed_section = f"\n### ⚠️ Skipped / Failed in this Run:\n" + "\n".join(failed_lines) + "\n*(You can re-run the workflow for this specific app with a custom URL)*\n"

    release_body = f"""## 🚀 Morphe Patched Apps Release

### 📱 Built Applications ({len(built_summary)}):
{chr(10).join(summary_table)}
{failed_section}
- **Architecture:** `{arch}`
- **Install Mode:** `{install_type}`

---

### 📋 Official Morphe Patches Changelog ({patches_tag}):
{patches_changelog}

---

### 🔐 File Integrity (SHA-256):
{chr(10).join(checksum_lines)}

> 💡 *Non-Root users must install MicroG (GmsCore) included in the assets below to sign in to Google accounts.*
"""

    with open("output/release_notes.md", "w") as rf:
        rf.write(release_body)
    print(f"✅ Build finished: {len(built_summary)} apps built successfully! ({len(failed_summary)} skipped)")

if __name__ == "__main__":
    main()
