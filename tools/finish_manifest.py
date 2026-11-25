"""Writes a mod's finished mod.json from its source mod.json and the mod kit's hooks.json.

    python3 -I finish_manifest.py <source mod.json> <hooks.json> <out mod.json>

Follows docs/sdk/building.md, "The finished mod.json".
"""
import json
import re
import sys


def target_id(t):
    if t["kind"] == "class":
        raw = t["class"]
    else:
        raw = t["owner"] + "." + t["name"]
    raw = raw.replace("/", ".").replace("$", "_")
    return re.sub(r"[^A-Za-z0-9_.\-]", "", raw)


def main():
    src, hooks_path, out = sys.argv[1:4]
    with open(src, encoding="utf-8") as f:
        mod = json.load(f)
    with open(hooks_path, encoding="utf-8") as f:
        kit = json.load(f)

    targets = mod.setdefault("targets", {})
    hooks = mod.setdefault("hooks", [])

    def add_target(t):
        for tid, existing in targets.items():
            if existing == t:
                return tid
        base = target_id(t)
        tid, n = base, 2
        while tid in targets:
            tid, n = "%s-%d" % (base, n), n + 1
        targets[tid] = t
        return tid

    for h in kit.get("hooks", []):
        entry = {"id": h["id"], "target": add_target(h["target"]), "transform": h["transform"]}
        if "returns" in h:
            entry["returns"] = h["returns"]
        if "method_from" in h:
            entry["method"] = {"from_target": add_target(h["method_from"])}
            entry["body"] = {"template": h["body"]}
        if "match" in h:
            m = h["match"]
            if "target" in m:
                entry["match"] = {"opcode": m["opcode"], "target": add_target(m["target"]),
                                  "occurrence": m["occurrence"]}
            else:
                entry["match"] = dict(m)
        entry["bridge"] = h["bridge"]
        hooks.append(entry)

    classes = kit.get("hook_classes", [])
    if len(classes) > 1:
        sys.exit("FINISH_MANIFEST_ERROR more than one hook class: %s" % classes)
    if classes:
        helper = {"jar": "helper.jar", "class": classes[0]}
        if "helper" in mod and mod["helper"].get("class") != classes[0]:
            sys.exit("FINISH_MANIFEST_ERROR helper class in mod.json differs from the hook class")
        mod["helper"] = helper

    fields = []
    for fl in kit.get("fields", []):
        owner = add_target({"kind": "class", "class": fl["owner"]})
        for cls in re.findall(r"L([^;]+);", fl["descriptor"]):
            if not cls.startswith("java/"):
                add_target({"kind": "class", "class": cls})
        fields.append({"id": fl["id"], "owner": owner, "name": fl["name"], "descriptor": fl["descriptor"]})
    if fields:
        mod["fields"] = fields

    access = kit.get("access", [])
    if access:
        mod["opens"] = sorted({add_target(a) for a in access})

    order = ["manifest_version", "mod", "built_against", "helper", "targets", "hooks", "fields", "opens"]
    ordered = {k: mod[k] for k in order if k in mod}
    ordered.update({k: v for k, v in mod.items() if k not in ordered})
    with open(out, "w", encoding="utf-8") as f:
        json.dump(ordered, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("FINISH_MANIFEST_OK hooks=%d targets=%d opens=%d" % (len(hooks), len(targets), len(access)))


main()
