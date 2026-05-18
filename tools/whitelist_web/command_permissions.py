import os
import re


PERMISSION_COMMANDS = {
    "creative": {
        "label": "/creative",
        "config": "command-control",
        "token": "creative",
        "reload": "xcc reload",
    },
    "xcc.reload": {
        "label": "/xcc reload",
        "config": "command-control",
        "token": "xcc.reload",
        "reload": "xcc reload",
    },
    "xcc.list": {
        "label": "/xcc list",
        "config": "command-control",
        "token": "xcc.list",
        "reload": "xcc reload",
    },
    "claim.give": {
        "label": "/claim give",
        "config": "claim",
        "token": "give",
        "reload": "claim reload",
    },
}


class CommandPermissionStore:
    def __init__(self, command_control_config_path, claim_config_path):
        self.command_control_config_path = command_control_config_path
        self.claim_config_path = claim_config_path

    def normalize_command(self, command_id):
        normalized = str(command_id or "creative").strip().lower()
        if normalized.startswith("/"):
            normalized = normalized[1:]
        normalized = normalized.replace(" ", ".")
        if normalized not in PERMISSION_COMMANDS:
            raise ValueError("指令不在可管理范围内。")
        return normalized

    def command(self, command_id):
        return PERMISSION_COMMANDS[self.normalize_command(command_id)]

    def assignments(self, command_id):
        command_id = self.normalize_command(command_id)
        command = PERMISSION_COMMANDS[command_id]
        config = self.read_config(command["config"])
        token = command["token"].lower()
        rows = []
        for player_uuid, entry in config["players"].items():
            if token not in {value.lower() for value in entry["commands"]}:
                continue
            rows.append({
                "uuid": player_uuid,
                "name": entry.get("name", ""),
            })
        return rows

    def grant(self, entry, command_id):
        command_id = self.normalize_command(command_id)
        command = PERMISSION_COMMANDS[command_id]
        config = self.read_config(command["config"])
        player_uuid = canonical_uuid(entry["uuid"])
        player_config = config["players"].setdefault(player_uuid, {"name": entry["name"], "commands": []})
        player_config["name"] = entry["name"]
        tokens = {value.lower() for value in player_config["commands"]}
        if command["token"].lower() not in tokens:
            player_config["commands"].append(command["token"])
            self.write_config(command["config"], config)
        return command.get("reload")

    def revoke(self, player_uuid, command_id):
        command_id = self.normalize_command(command_id)
        command = PERMISSION_COMMANDS[command_id]
        config = self.read_config(command["config"])
        normalized_uuid = canonical_uuid(player_uuid)
        player_config = config["players"].get(normalized_uuid)
        if not player_config:
            return command.get("reload")
        before = list(player_config["commands"])
        player_config["commands"] = [
            value for value in player_config["commands"]
            if value.lower() != command["token"].lower()
        ]
        if before != player_config["commands"]:
            if not player_config["commands"]:
                config["players"].pop(normalized_uuid, None)
            self.write_config(command["config"], config)
        return command.get("reload")

    def config_path(self, config_name):
        if config_name == "command-control":
            return self.command_control_config_path
        if config_name == "claim":
            return self.claim_config_path
        raise ValueError("未知权限配置。")

    def read_config(self, config_name):
        path = self.config_path(config_name)
        try:
            with open(path, "r", encoding="utf-8") as file:
                text = file.read()
        except FileNotFoundError:
            text = ""
        if config_name == "command-control":
            return {
                "default": parse_top_level_list(text, "default-allowed-commands"),
                "players": parse_players_block(text, "players", "commands"),
            }
        if config_name == "claim":
            access_text = extract_top_level_block(text, "access")
            return {
                "default": parse_nested_list(access_text, "default-allowed-actions"),
                "players": parse_nested_players_block(access_text, "players", "actions"),
            }
        raise ValueError("未知权限配置。")

    def write_config(self, config_name, config):
        path = self.config_path(config_name)
        try:
            with open(path, "r", encoding="utf-8") as file:
                text = file.read()
        except FileNotFoundError:
            text = ""
        if config_name == "command-control":
            new_text = replace_top_level_block(
                text,
                "players",
                render_permission_players_block(config["players"], "players", "commands"),
            )
        elif config_name == "claim":
            new_text = replace_top_level_block(text, "access", render_claim_access_block(config))
        else:
            raise ValueError("未知权限配置。")
        os.makedirs(os.path.dirname(path), exist_ok=True)
        temp_path = path + ".tmp"
        with open(temp_path, "w", encoding="utf-8") as file:
            file.write(new_text.rstrip() + "\n")
        os.replace(temp_path, path)


def canonical_uuid(value):
    compact = str(value or "").replace("-", "").lower()
    if len(compact) != 32:
        return str(value or "")
    return f"{compact[0:8]}-{compact[8:12]}-{compact[12:16]}-{compact[16:20]}-{compact[20:32]}"


def yaml_scalar(value):
    value = value.strip()
    if not value:
        return ""
    if value[0:1] in {"'", '"'} and value[-1:] == value[0]:
        return value[1:-1]
    return value


def parse_top_level_list(text, key):
    lines = text.splitlines()
    values = []
    for index, line in enumerate(lines):
        if line.strip() == f"{key}:":
            for child in lines[index + 1:]:
                indent = len(child) - len(child.lstrip(" "))
                stripped = child.strip()
                if not stripped or stripped.startswith("#"):
                    continue
                if indent == 0:
                    break
                if stripped.startswith("- "):
                    values.append(yaml_scalar(stripped[2:]))
            break
    return values


def parse_nested_list(text, key):
    lines = text.splitlines()
    values = []
    for index, line in enumerate(lines):
        if line.startswith(f"  {key}:"):
            inline = line.split(":", 1)[1].strip()
            if inline and inline != "[]":
                return [yaml_scalar(item.strip()) for item in inline.strip("[]").split(",") if item.strip()]
            for child in lines[index + 1:]:
                indent = len(child) - len(child.lstrip(" "))
                stripped = child.strip()
                if not stripped or stripped.startswith("#"):
                    continue
                if indent <= 2:
                    break
                if stripped.startswith("- "):
                    values.append(yaml_scalar(stripped[2:]))
            break
    return values


def parse_players_block(text, block_key, list_key):
    lines = text.splitlines()
    players = {}
    current_uuid = None
    current_list = None
    in_block = False
    for raw_line in lines:
        if not raw_line.strip() or raw_line.lstrip().startswith("#"):
            continue
        indent = len(raw_line) - len(raw_line.lstrip(" "))
        stripped = raw_line.strip()
        if indent == 0:
            in_block = stripped == f"{block_key}:"
            current_uuid = None
            current_list = None
            continue
        if not in_block:
            continue
        if indent == 2 and stripped.endswith(":"):
            current_uuid = canonical_uuid(stripped[:-1])
            players[current_uuid] = {"name": "", "commands": []}
            current_list = None
            continue
        if current_uuid is None:
            continue
        if indent == 4 and ":" in stripped:
            key, value = stripped.split(":", 1)
            key = key.strip()
            value = value.strip()
            if key == "name":
                players[current_uuid]["name"] = yaml_scalar(value)
                current_list = None
            elif key == list_key:
                current_list = "commands"
                if value and value != "[]":
                    players[current_uuid]["commands"] = [
                        yaml_scalar(item.strip())
                        for item in value.strip("[]").split(",")
                        if item.strip()
                    ]
            continue
        if indent >= 6 and current_list == "commands" and stripped.startswith("- "):
            players[current_uuid]["commands"].append(yaml_scalar(stripped[2:]))
    return players


def parse_nested_players_block(text, block_key, list_key):
    lines = text.splitlines()
    players = {}
    current_uuid = None
    current_list = None
    in_block = False
    for raw_line in lines:
        if not raw_line.strip() or raw_line.lstrip().startswith("#"):
            continue
        indent = len(raw_line) - len(raw_line.lstrip(" "))
        stripped = raw_line.strip()
        if indent <= 2:
            in_block = indent == 2 and stripped == f"{block_key}:"
            current_uuid = None
            current_list = None
            continue
        if not in_block:
            continue
        if indent == 4 and stripped.endswith(":"):
            current_uuid = canonical_uuid(stripped[:-1])
            players[current_uuid] = {"name": "", "commands": []}
            current_list = None
            continue
        if current_uuid is None:
            continue
        if indent == 6 and ":" in stripped:
            key, value = stripped.split(":", 1)
            key = key.strip()
            value = value.strip()
            if key == "name":
                players[current_uuid]["name"] = yaml_scalar(value)
                current_list = None
            elif key == list_key:
                current_list = "commands"
                if value and value != "[]":
                    players[current_uuid]["commands"] = [
                        yaml_scalar(item.strip())
                        for item in value.strip("[]").split(",")
                        if item.strip()
                    ]
            continue
        if indent >= 8 and current_list == "commands" and stripped.startswith("- "):
            players[current_uuid]["commands"].append(yaml_scalar(stripped[2:]))
    return players


def extract_top_level_block(text, key):
    lines = text.splitlines()
    start = None
    end = len(lines)
    for index, line in enumerate(lines):
        if line.strip() == f"{key}:" and len(line) - len(line.lstrip(" ")) == 0:
            start = index
            continue
        if start is not None and index > start and line.strip() and len(line) - len(line.lstrip(" ")) == 0:
            end = index
            break
    if start is None:
        return ""
    return "\n".join(lines[start:end])


def replace_top_level_block(text, key, replacement):
    lines = text.splitlines()
    start = None
    end = len(lines)
    for index, line in enumerate(lines):
        if line.strip() == f"{key}:" and len(line) - len(line.lstrip(" ")) == 0:
            start = index
            continue
        if start is not None and index > start and line.strip() and len(line) - len(line.lstrip(" ")) == 0:
            end = index
            break
    if start is None:
        if text.strip():
            return text.rstrip() + "\n\n" + replacement
        return replacement
    return "\n".join(lines[:start] + replacement.splitlines() + lines[end:])


def render_permission_players_block(players, block_key, list_key):
    lines = [f"{block_key}:"]
    for player_uuid, entry in sorted(players.items(), key=lambda item: (str(item[1].get("name", "")).lower(), item[0])):
        commands = sorted({str(value) for value in entry.get("commands", []) if value}, key=str.lower)
        if not commands:
            continue
        lines.append(f"  {canonical_uuid(player_uuid)}:")
        lines.append(f"    name: {yaml_quote(entry.get('name', ''))}")
        lines.append(f"    {list_key}:")
        for command in commands:
            lines.append(f"      - {yaml_quote(command)}")
    return "\n".join(lines)


def render_claim_access_block(config):
    lines = ["access:"]
    default_actions = [value for value in config.get("default", []) if value]
    if default_actions:
        lines.append("  default-allowed-actions:")
        for action in sorted(default_actions, key=str.lower):
            lines.append(f"    - {yaml_quote(action)}")
    else:
        lines.append("  default-allowed-actions: []")
    player_block = render_permission_players_block(config["players"], "players", "actions").splitlines()
    lines.extend("  " + line for line in player_block)
    return "\n".join(lines)


def yaml_quote(value):
    text = str(value or "")
    if re.fullmatch(r"[A-Za-z0-9_.-]+", text):
        return text
    return "'" + text.replace("'", "''") + "'"
