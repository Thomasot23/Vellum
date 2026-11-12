// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 Fabian Schmieder. All rights reserved.
"use client";

/**
 * Branding for the room-booking status layouts: wordmark lines, a location line
 * and an uploaded mark.
 *
 * The mark is any SVG or PNG; the renderer draws it as a silhouette in a theme
 * colour, so one file serves every panel and both states. An SVG with explicit
 * `width` and `height` keeps its proportions; a bare `viewBox` falls back to 1:2.
 */

import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { Input } from "@/components/ui/field";
import { recordString } from "@/lib/record-value";

interface Asset {
  id: string;
  name: string;
  mimeType: string;
}

interface Props {
  config: Record<string, unknown>;
  onChange: (config: Record<string, unknown>) => void;
  selectCls: string;
}

export function BrandFields({ config, onChange, selectCls }: Props) {
  const t = useTranslations("content");
  const [assets, setAssets] = useState<Asset[]>([]);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    fetch("/api/v1/admin/assets?limit=200", { cache: "no-store" })
      .then((r) => (r.ok ? (r.json() as Promise<Asset[]>) : []))
      .then((rows) => {
        if (live) setAssets(rows.filter((a) => a.mimeType.startsWith("image/")));
      })
      .catch(() => {
        if (live) setAssets([]);
      });
    return () => {
      live = false;
    };
  }, []);

  /* Clearing a text field removes the key, so the stored config stays minimal. */
  function set(key: string, value: string) {
    const next = Object.fromEntries(Object.entries(config).filter(([k]) => k !== key));
    if (value.trim()) next[key] = value;
    onChange(next);
  }

  async function upload(file: File) {
    setUploading(true);
    setError(null);
    try {
      const form = new FormData();
      form.append("file", file);
      form.append("name", file.name);
      const res = await fetch("/api/v1/admin/assets", { method: "POST", body: form });
      const body = (await res.json()) as Asset & { error?: string };
      if (!res.ok) throw new Error(body.error ?? res.statusText);
      setAssets((prev) => [...prev, body]);
      onChange({ ...config, brandMarkAssetId: body.id });
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="border-t border-separator pt-3 mt-1 mb-3">
      <label className="block text-sm font-semibold text-label mb-1">{t("branding")}</label>
      <p className="text-xs text-label-tertiary mb-2">{t("brandingHint")}</p>

      <label className="block text-sm font-medium text-label-secondary mb-1">
        {t("brandTitle")}
      </label>
      <textarea
        className={`${selectCls} mb-2 py-1.5`}
        rows={2}
        value={recordString(config, "brandTitle")}
        onChange={(e) => set("brandTitle", e.target.value)}
      />

      <label className="block text-sm font-medium text-label-secondary mb-1">
        {t("brandSubtitle")}
      </label>
      <textarea
        className={`${selectCls} mb-2 py-1.5`}
        rows={3}
        value={recordString(config, "brandSubtitle")}
        onChange={(e) => set("brandSubtitle", e.target.value)}
      />

      <label className="block text-sm font-medium text-label-secondary mb-1">
        {t("roomSubtitle")}
      </label>
      <Input
        className="mb-2"
        value={recordString(config, "roomSubtitle")}
        onChange={(e) => set("roomSubtitle", e.target.value)}
      />

      <label className="block text-sm font-medium text-label-secondary mb-1">
        {t("brandMark")}
      </label>
      <select
        className={`${selectCls} mb-2`}
        value={recordString(config, "brandMarkAssetId")}
        onChange={(e) => set("brandMarkAssetId", e.target.value)}
      >
        <option value="">{t("brandMarkNone")}</option>
        {assets.map((a) => (
          <option key={a.id} value={a.id}>
            {a.name}
          </option>
        ))}
      </select>
      <label className="block text-xs text-label-secondary">
        {t("brandMarkUpload")}
        <input
          type="file"
          accept="image/svg+xml,image/png"
          className="block mt-1 text-xs"
          disabled={uploading}
          onChange={(e) => {
            const file = e.target.files?.[0];
            if (file) void upload(file);
            e.target.value = "";
          }}
        />
      </label>
      {error && <p className="text-xs text-red mt-1">{error}</p>}
    </div>
  );
}
