import { redirect } from "next/navigation";

/** L'ancienne fiche détaillée est remplacée par l'écran maître / détail de /filieres. */
export default async function FiliereRedirect({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  redirect(`/filieres?id=${encodeURIComponent(id)}`);
}
